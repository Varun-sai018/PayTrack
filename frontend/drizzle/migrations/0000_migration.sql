-- ===== Enums =====
CREATE TYPE public.txn_type AS ENUM ('TRANSFER', 'RECHARGE');
CREATE TYPE public.txn_status AS ENUM ('SUCCESS', 'PENDING', 'FAILED');
CREATE TYPE public.expense_category AS ENUM ('FOOD','TRANSPORT','HOUSING','ENTERTAINMENT','UTILITIES','HEALTHCARE','SHOPPING','EDUCATION','OTHER');
CREATE TYPE public.expense_source AS ENUM ('MANUAL', 'PAYMENT');
CREATE TYPE public.ledger_entry_type AS ENUM ('CREDIT', 'DEBIT');

-- ===== Profiles =====
CREATE TABLE public.profiles (
  id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  full_name text NOT NULL DEFAULT '',
  email text NOT NULL DEFAULT '',
  phone text UNIQUE CHECK (phone IS NULL OR phone ~ '^[6-9][0-9]{9}$'),
  upi_id text GENERATED ALWAYS AS (CASE WHEN phone IS NULL THEN NULL ELSE phone || '@paytrack' END) STORED UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
GRANT SELECT ON public.profiles TO authenticated;
GRANT ALL ON public.profiles TO service_role;
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users read own profile" ON public.profiles FOR SELECT TO authenticated USING (id = auth.uid());

-- ===== Wallets =====
CREATE TABLE public.wallets (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL UNIQUE REFERENCES public.profiles(id) ON DELETE CASCADE,
  balance numeric(14,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
  currency text NOT NULL DEFAULT 'INR',
  status text NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
GRANT SELECT ON public.wallets TO authenticated;
GRANT ALL ON public.wallets TO service_role;
ALTER TABLE public.wallets ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users read own wallet" ON public.wallets FOR SELECT TO authenticated USING (user_id = auth.uid());

-- ===== Ledger entries =====
CREATE TABLE public.ledger_entries (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  wallet_id uuid NOT NULL REFERENCES public.wallets(id) ON DELETE CASCADE,
  user_id uuid NOT NULL,
  entry_type public.ledger_entry_type NOT NULL,
  amount numeric(14,2) NOT NULL CHECK (amount > 0),
  balance_after numeric(14,2) NOT NULL,
  description text,
  reference_id text,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ledger_entries_user_created_idx ON public.ledger_entries (user_id, created_at DESC);
GRANT SELECT ON public.ledger_entries TO authenticated;
GRANT ALL ON public.ledger_entries TO service_role;
ALTER TABLE public.ledger_entries ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users read own ledger" ON public.ledger_entries FOR SELECT TO authenticated USING (user_id = auth.uid());

-- ===== Transactions =====
CREATE TABLE public.transactions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  txn_ref text NOT NULL UNIQUE,
  user_id uuid NOT NULL,
  receiver_user_id uuid,
  sender_upi_id text,
  receiver_upi_id text,
  type public.txn_type NOT NULL,
  status public.txn_status NOT NULL DEFAULT 'SUCCESS',
  amount numeric(14,2) NOT NULL CHECK (amount > 0),
  description text,
  requested_category public.expense_category,
  operator text,
  mobile_number text,
  idempotency_key text,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id, idempotency_key)
);
CREATE INDEX transactions_user_created_idx ON public.transactions (user_id, created_at DESC);
CREATE INDEX transactions_receiver_created_idx ON public.transactions (receiver_user_id, created_at DESC);
GRANT SELECT ON public.transactions TO authenticated;
GRANT ALL ON public.transactions TO service_role;
ALTER TABLE public.transactions ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users read own transactions" ON public.transactions FOR SELECT TO authenticated
  USING (user_id = auth.uid() OR receiver_user_id = auth.uid());

-- ===== Expenses =====
CREATE TABLE public.expenses (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL DEFAULT auth.uid(),
  category public.expense_category NOT NULL DEFAULT 'OTHER',
  amount numeric(14,2) NOT NULL CHECK (amount > 0),
  currency text NOT NULL DEFAULT 'INR',
  description text,
  source public.expense_source NOT NULL DEFAULT 'MANUAL',
  txn_ref text,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX expenses_user_created_idx ON public.expenses (user_id, created_at DESC);
GRANT SELECT, INSERT, DELETE ON public.expenses TO authenticated;
GRANT UPDATE (category) ON public.expenses TO authenticated;
GRANT ALL ON public.expenses TO service_role;
ALTER TABLE public.expenses ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users read own expenses" ON public.expenses FOR SELECT TO authenticated USING (user_id = auth.uid());
CREATE POLICY "Users add manual expenses" ON public.expenses FOR INSERT TO authenticated
  WITH CHECK (user_id = auth.uid() AND source = 'MANUAL' AND txn_ref IS NULL);
CREATE POLICY "Users recategorise own expenses" ON public.expenses FOR UPDATE TO authenticated
  USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid());
CREATE POLICY "Users delete manual expenses" ON public.expenses FOR DELETE TO authenticated
  USING (user_id = auth.uid() AND source = 'MANUAL');

-- ===== Helpers =====
CREATE OR REPLACE FUNCTION public.new_txn_ref()
RETURNS text LANGUAGE sql VOLATILE SET search_path = '' AS $$
  SELECT 'PT' || to_char(now(), 'YYMMDD') || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 10));
$$;

CREATE OR REPLACE FUNCTION public.mask_name(p_name text)
RETURNS text LANGUAGE sql IMMUTABLE SET search_path = '' AS $$
  SELECT string_agg(
    CASE WHEN length(w) <= 2 THEN left(w, 1) || '*'
         ELSE left(w, 1) || repeat('*', length(w) - 2) || right(w, 1) END, ' ')
  FROM unnest(regexp_split_to_array(trim(coalesce(p_name, '')), '\s+')) AS w
  WHERE w <> '';
$$;

-- ===== Signup: create profile + wallet =====
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
  v_phone text := NEW.raw_user_meta_data->>'phone';
BEGIN
  IF v_phone IS NULL OR v_phone !~ '^[6-9][0-9]{9}$'
     OR EXISTS (SELECT 1 FROM public.profiles WHERE phone = v_phone) THEN
    v_phone := NULL;
  END IF;
  INSERT INTO public.profiles (id, full_name, email, phone)
  VALUES (NEW.id, coalesce(NEW.raw_user_meta_data->>'full_name', ''), coalesce(NEW.email, ''), v_phone);
  INSERT INTO public.wallets (user_id) VALUES (NEW.id);
  RETURN NEW;
END;
$$;
CREATE TRIGGER on_auth_user_created AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- ===== Public: is a mobile number free? =====
CREATE OR REPLACE FUNCTION public.phone_available(p_phone text)
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path = '' AS $$
  SELECT NOT EXISTS (SELECT 1 FROM public.profiles WHERE phone = p_phone);
$$;

-- ===== Profile update =====
CREATE OR REPLACE FUNCTION public.update_my_profile(p_full_name text, p_phone text)
RETURNS public.profiles LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
  v_uid uuid := auth.uid();
  v_row public.profiles;
  v_phone text := nullif(trim(coalesce(p_phone, '')), '');
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'Not signed in'; END IF;
  IF length(trim(coalesce(p_full_name, ''))) = 0 THEN RAISE EXCEPTION 'Full name is required'; END IF;
  IF v_phone IS NOT NULL AND v_phone !~ '^[6-9][0-9]{9}$' THEN
    RAISE EXCEPTION 'Please enter a valid 10-digit Indian mobile number (starts with 6-9)';
  END IF;
  IF v_phone IS NOT NULL AND EXISTS (SELECT 1 FROM public.profiles WHERE phone = v_phone AND id <> v_uid) THEN
    RAISE EXCEPTION 'This mobile number is already registered';
  END IF;
  UPDATE public.profiles SET full_name = trim(p_full_name), phone = v_phone, updated_at = now()
  WHERE id = v_uid RETURNING * INTO v_row;
  RETURN v_row;
END;
$$;

-- ===== Recipient lookup =====
CREATE OR REPLACE FUNCTION public.lookup_upi(p_upi text)
RETURNS TABLE (upi_id text, masked_name text) LANGUAGE sql STABLE SECURITY DEFINER SET search_path = '' AS $$
  SELECT p.upi_id, public.mask_name(p.full_name)
  FROM public.profiles p
  WHERE auth.uid() IS NOT NULL AND p.upi_id = lower(trim(p_upi));
$$;

-- ===== Sandbox top-up =====
CREATE OR REPLACE FUNCTION public.wallet_topup(p_amount numeric)
RETURNS public.wallets LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
  v_uid uuid := auth.uid();
  v_amount numeric(14,2) := round(p_amount, 2);
  v_wallet public.wallets;
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'Not signed in'; END IF;
  IF v_amount IS NULL OR v_amount < 1 THEN RAISE EXCEPTION 'Minimum top-up amount is ₹1.00'; END IF;
  UPDATE public.wallets SET balance = balance + v_amount, updated_at = now()
  WHERE user_id = v_uid RETURNING * INTO v_wallet;
  IF NOT FOUND THEN RAISE EXCEPTION 'Wallet not found'; END IF;
  INSERT INTO public.ledger_entries (wallet_id, user_id, entry_type, amount, balance_after, description, reference_id)
  VALUES (v_wallet.id, v_uid, 'CREDIT', v_amount, v_wallet.balance, 'Wallet top-up (sandbox UPI)', 'TOPUP-' || public.new_txn_ref());
  RETURN v_wallet;
END;
$$;

-- ===== UPI transfer =====
CREATE OR REPLACE FUNCTION public.transfer_money(
  p_receiver_upi text, p_amount numeric, p_note text, p_category public.expense_category, p_idempotency_key text)
RETURNS public.transactions LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
  v_uid uuid := auth.uid();
  v_amount numeric(14,2) := round(p_amount, 2);
  v_upi text := lower(trim(coalesce(p_receiver_upi, '')));
  v_note text := nullif(trim(coalesce(p_note, '')), '');
  v_receiver public.profiles;
  v_sender public.profiles;
  v_sw public.wallets;
  v_rw public.wallets;
  v_txn public.transactions;
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'Not signed in'; END IF;
  IF p_idempotency_key IS NOT NULL THEN
    SELECT * INTO v_txn FROM public.transactions WHERE user_id = v_uid AND idempotency_key = p_idempotency_key;
    IF FOUND THEN RETURN v_txn; END IF;
  END IF;
  IF v_amount IS NULL OR v_amount < 1 OR v_amount > 10000 THEN
    RAISE EXCEPTION 'Amount must be between ₹1.00 and ₹10,000.00';
  END IF;
  IF right(v_upi, 9) <> '@paytrack' THEN RAISE EXCEPTION 'UPI ID must end with @paytrack'; END IF;
  SELECT * INTO v_receiver FROM public.profiles WHERE upi_id = v_upi;
  IF NOT FOUND THEN RAISE EXCEPTION 'User not found with this UPI ID'; END IF;
  IF v_receiver.id = v_uid THEN RAISE EXCEPTION 'You cannot send money to yourself'; END IF;
  SELECT * INTO v_sender FROM public.profiles WHERE id = v_uid;

  -- Lock both wallets in a stable order to avoid deadlocks
  PERFORM 1 FROM public.wallets WHERE user_id IN (v_uid, v_receiver.id) ORDER BY id FOR UPDATE;
  SELECT * INTO v_sw FROM public.wallets WHERE user_id = v_uid;
  IF v_sw.balance < v_amount THEN RAISE EXCEPTION 'Insufficient wallet balance'; END IF;

  UPDATE public.wallets SET balance = balance - v_amount, updated_at = now() WHERE id = v_sw.id RETURNING * INTO v_sw;
  UPDATE public.wallets SET balance = balance + v_amount, updated_at = now() WHERE user_id = v_receiver.id RETURNING * INTO v_rw;

  INSERT INTO public.transactions (txn_ref, user_id, receiver_user_id, sender_upi_id, receiver_upi_id, type, status, amount, description, requested_category, idempotency_key)
  VALUES (public.new_txn_ref(), v_uid, v_receiver.id, v_sender.upi_id, v_upi, 'TRANSFER', 'SUCCESS', v_amount, v_note, coalesce(p_category, 'OTHER'), p_idempotency_key)
  RETURNING * INTO v_txn;

  INSERT INTO public.ledger_entries (wallet_id, user_id, entry_type, amount, balance_after, description, reference_id) VALUES
    (v_sw.id, v_uid, 'DEBIT', v_amount, v_sw.balance, 'UPI transfer to ' || v_upi, v_txn.txn_ref),
    (v_rw.id, v_receiver.id, 'CREDIT', v_amount, v_rw.balance, 'UPI received from ' || coalesce(v_sender.upi_id, 'PayTrack user'), v_txn.txn_ref);

  INSERT INTO public.expenses (user_id, category, amount, description, source, txn_ref)
  VALUES (v_uid, coalesce(p_category, 'OTHER'), v_amount, coalesce(v_note, 'UPI transfer to ' || v_upi), 'PAYMENT', v_txn.txn_ref);

  RETURN v_txn;
END;
$$;

-- ===== Mobile recharge =====
CREATE OR REPLACE FUNCTION public.mobile_recharge(
  p_mobile text, p_operator text, p_amount numeric, p_idempotency_key text)
RETURNS public.transactions LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
  v_uid uuid := auth.uid();
  v_amount numeric(14,2) := round(p_amount, 2);
  v_sw public.wallets;
  v_sender public.profiles;
  v_txn public.transactions;
  v_desc text;
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'Not signed in'; END IF;
  IF p_idempotency_key IS NOT NULL THEN
    SELECT * INTO v_txn FROM public.transactions WHERE user_id = v_uid AND idempotency_key = p_idempotency_key;
    IF FOUND THEN RETURN v_txn; END IF;
  END IF;
  IF v_amount IS NULL OR v_amount < 10 OR v_amount > 2000 THEN
    RAISE EXCEPTION 'Recharge amount must be between ₹10.00 and ₹2,000.00';
  END IF;
  IF coalesce(p_mobile, '') !~ '^[0-9]{10}$' THEN RAISE EXCEPTION 'Mobile number must be exactly 10 digits'; END IF;
  IF p_operator NOT IN ('JIO', 'AIRTEL', 'VI', 'BSNL') THEN RAISE EXCEPTION 'Unsupported operator'; END IF;

  SELECT * INTO v_sw FROM public.wallets WHERE user_id = v_uid FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Wallet not found'; END IF;
  IF v_sw.balance < v_amount THEN RAISE EXCEPTION 'Insufficient wallet balance'; END IF;
  SELECT * INTO v_sender FROM public.profiles WHERE id = v_uid;

  UPDATE public.wallets SET balance = balance - v_amount, updated_at = now() WHERE id = v_sw.id RETURNING * INTO v_sw;
  v_desc := p_operator || ' recharge for ' || p_mobile;

  INSERT INTO public.transactions (txn_ref, user_id, sender_upi_id, type, status, amount, description, requested_category, operator, mobile_number, idempotency_key)
  VALUES (public.new_txn_ref(), v_uid, v_sender.upi_id, 'RECHARGE', 'SUCCESS', v_amount, v_desc, 'UTILITIES', p_operator, p_mobile, p_idempotency_key)
  RETURNING * INTO v_txn;

  INSERT INTO public.ledger_entries (wallet_id, user_id, entry_type, amount, balance_after, description, reference_id)
  VALUES (v_sw.id, v_uid, 'DEBIT', v_amount, v_sw.balance, v_desc, v_txn.txn_ref);

  INSERT INTO public.expenses (user_id, category, amount, description, source, txn_ref)
  VALUES (v_uid, 'UTILITIES', v_amount, v_desc, 'PAYMENT', v_txn.txn_ref);

  RETURN v_txn;
END;
$$;

-- ===== Function privileges =====
REVOKE EXECUTE ON FUNCTION public.handle_new_user() FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public.phone_available(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.phone_available(text) TO anon, authenticated;
REVOKE EXECUTE ON FUNCTION public.update_my_profile(text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.update_my_profile(text, text) TO authenticated;
REVOKE EXECUTE ON FUNCTION public.lookup_upi(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.lookup_upi(text) TO authenticated;
REVOKE EXECUTE ON FUNCTION public.wallet_topup(numeric) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.wallet_topup(numeric) TO authenticated;
REVOKE EXECUTE ON FUNCTION public.transfer_money(text, numeric, text, public.expense_category, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.transfer_money(text, numeric, text, public.expense_category, text) TO authenticated;
REVOKE EXECUTE ON FUNCTION public.mobile_recharge(text, text, numeric, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobile_recharge(text, text, numeric, text) TO authenticated;
