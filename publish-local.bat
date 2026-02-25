@echo off
echo ========================================
echo Publicando kmplib no Maven Local...
echo ========================================
echo.

cd /d "%~dp0"
call gradlew.bat clean publishToMavenLocal

if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo * Publicado com sucesso!
    echo ========================================
    echo.
    echo Versao: 1.0.0
    echo Group: br.com.codecacto
    echo Artifact: kmplib
    echo.
    echo Local: %USERPROFILE%\.m2\repository\br\com\codecacto\kmplib\1.0.0
    echo.
    echo ========================================
    echo Para usar nos seus projetos:
    echo ========================================
    echo.
    echo 1. Adicione no settings.gradle.kts:
    echo    dependencyResolutionManagement {
    echo        repositories {
    echo            mavenLocal^(^)  // Adicionar PRIMEIRO
    echo            google^(^)
    echo            mavenCentral^(^)
    echo        }
    echo    }
    echo.
    echo 2. Adicione no build.gradle.kts:
    echo    implementation^("br.com.codecacto:kmplib:1.0.0"^)
    echo.
    echo 3. Sync Gradle
    echo.
    echo ========================================
    echo Projetos que podem usar:
    echo ========================================
    echo - E:\CodeCacto\Locadora
    echo - E:\CodeCacto\Meu Advogado
    echo - E:\CodeCacto\Medicamentos
    echo - E:\TechPromos\mobile
    echo.
) else (
    echo.
    echo ========================================
    echo X Erro na publicacao
    echo ========================================
    echo.
    echo Verifique os logs acima para detalhes.
    echo.
)

pause
