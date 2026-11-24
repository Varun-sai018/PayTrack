FROM node:20-alpine
WORKDIR /app

COPY package*.json ./
RUN npm install

COPY . .

EXPOSE 80
ENV PORT=80
ENV HOST=0.0.0.0
ENV NODE_OPTIONS="--insecure-http-parser"

CMD ["npx", "vite", "dev", "--host", "0.0.0.0", "--port", "80"]
