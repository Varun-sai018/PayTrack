@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Maven Start Up Batch script
@REM ----------------------------------------------------------------------------

@REM Environment variables required to run this script:
@REM   M2_HOME   - Location of maven installation directory (optional)
@REM   MAVEN_OPTS - Parameters passed to the Java VM when running Maven
@REM     e.g. to debug Maven itself, use
@REM       set MAVEN_OPTS=-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8000
@REM   MAVEN_DEBUG  - (optional) Set to true to enable debugging
@REM   MAVEN_SKIP_RC - (optional) Set to true to disable reading .mavenrc
@REM   MAVEN_SKIP_RC_WIN - (optional) Set to true to disable reading .mavenrc on Windows

@REM Begin all REM lines with @REM in case filename completion is being used
@REM @setlocal

@REM @set REM_ARGS=

@REM if not "%OS%"=="Windows_NT" goto win9x_args
@REM if "%1"=="" goto no_args
@REM   @rem Parse any arguments on command line
@REM   :parse_args
@REM     if "%1"=="" goto parsed_args
@REM     @set REM_ARGS=%REM_ARGS% %1
@REM     shift
@REM     goto parse_args
@REM   :parsed_args
@REM   @goto win9x_args
@REM   :no_args
@REM     @set REM_ARGS=
@REM   :win9x_args
@REM @rem "Caught by Java's _JAVA_OPTIONS" might look something like
@REM @rem "-Djava.library.path=...;...;..." so we strip off everything
@REM @rem after the last semicolon.
@REM if not "%_JAVA_OPTIONS%"=="" goto java_options
@REM goto done_java_options
@REM :java_options
@REM   @for %%i in (%_JAVA_OPTIONS%) do @set JAVA_OPTIONS=%%i
@REM   @goto done_java_options
@REM :done_java_options

@REM if not "%JAVA_HOME%"=="" goto OkJ
@REM echo.
@REM echo Error: JAVA_HOME not found in your environment. >&2
@REM echo Please set the JAVA_HOME variable in your environment to match the >&2
@REM echo location of your Java installation. >&2
@REM goto error
@REM :OkJ
@REM find java executable
@REM if "%JAVA_HOME%"=="%JAVA_HOME:/=\"%" set _cmd=%JAVA_HOME%\bin\java.exe
@REM if not exist "%_cmd%" goto setPath
@REM goto check_java_exe
@REM :setPath
@REM   set PATH=%JAVA_HOME%;%PATH%
@REM :check_java_exe
@REM if exist "%_cmd%" goto run
@REM echo.
@REM echo Error: JAVA_HOME is set to an invalid directory. >&2
@REM echo JAVA_HOME = "%JAVA_HOME%" >&2
@REM echo Please set the JAVA_HOME variable in your environment to match the >&2
@REM echo location of your Java installation. >&2
@REM goto error
@REM :run
@REM "%_cmd%" %JAVA_OPTIONS% -classpath "%CLASSPATH%" -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%" org.apache.maven.wrapper.MavenWrapperMain %*
@REM goto end
@REM :error
@REM set EXIT_CODE=1
@REM :end
@REM @rem ***** END OF SCRIPT *****
@REM @endlocal & set EXIT_CODE=%EXIT_CODE%

@REM For backward compatibility with Windows 9x/Me
if not "%OS%"=="Windows_NT" goto win9x_args

if "%1"=="" goto no_args

@rem Parse any arguments on command line
:parse_args
  if "%1"=="" goto parsed_args
  set REM_ARGS=%REM_ARGS% %1
  shift
  goto parse_args

:parsed_args
  goto win9x_args

:no_args
  set REM_ARGS=

:win9x_args

@rem "Caught by Java's _JAVA_OPTIONS" might look something like
@rem "-Djava.library.path=...;...;..." so we strip off everything
@rem after the last semicolon.
if not "%_JAVA_OPTIONS%"=="" goto java_options
goto done_java_options

:java_options
  for %%i in (%_JAVA_OPTIONS%) do set JAVA_OPTIONS=%%i
  goto done_java_options

:done_java_options

if not "%JAVA_HOME%"=="" goto OkJ

echo.
echo Error: JAVA_HOME not found in your environment. >&2
echo Please set the JAVA_HOME variable in your environment to match the >&2
echo location of your Java installation. >&2
goto error

:OkJ
rem find java executable
if "%JAVA_HOME%"=="%JAVA_HOME:/=\"%" set _cmd=%JAVA_HOME%\bin\java.exe
if not exist "%_cmd%" goto setPath
goto check_java_exe

:setPath
  set PATH=%JAVA_HOME%;%PATH%

:check_java_exe
if exist "%_cmd%" goto run
echo.
echo Error: JAVA_HOME is set to an invalid directory. >&2
echo JAVA_HOME = "%JAVA_HOME%" >&2
echo Please set the JAVA_HOME variable in your environment to match the >&2
echo location of your Java installation. >&2
goto error

:run
"%_cmd%" %JAVA_OPTIONS% -classpath "%CLASSPATH%" -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%" org.apache.maven.wrapper.MavenWrapperMain %*
goto end

:error
set EXIT_CODE=1

:end
@rem ***** END OF SCRIPT *****
@rem @endlocal & set EXIT_CODE=%EXIT_CODE%
