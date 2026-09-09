# Guali Tutor

Aplicación de escritorio (Swing) para acompañar lecciones de programación en Java: muestra el
material de la lección (Markdown con imágenes) junto a un editor de código y una consola de
salida.

## Requisitos

- **JDK 25** instalado (por ejemplo [Eclipse Temurin](https://adoptium.net/es/)).
- Conexión a internet la primera vez que se compila, para que Maven descargue las dependencias.

No es necesario tener Maven instalado por separado: el proyecto incluye el *Maven Wrapper*
(`mvnw` / `mvnw.cmd`), que descarga y usa automáticamente la versión correcta de Maven.

## Opción A — Sin Maven instalado (recomendado)

Usa el wrapper incluido en el proyecto. La primera vez descargará Maven por su cuenta; las
siguientes veces será instantáneo.

**macOS / Linux:**

```bash
./mvnw clean package
```

**Windows (cmd o PowerShell):**

```bat
mvnw.cmd clean package
```

Esto compila el proyecto y genera un **jar ejecutable** (con todas las dependencias incluidas)
en `target/guali-tutor-1.0.0.jar`.

Para ejecutar la aplicación:

```bash
java -jar target/guali-tutor-1.0.0.jar
```

(el comando es el mismo en las tres plataformas, siempre que tengas el JDK 25 en el `PATH`).

## Opción B — Con Maven instalado en el sistema

Si ya tienes Maven instalado (`mvn -v` funciona en tu terminal), puedes usarlo directamente en
lugar del wrapper; el resultado es idéntico:

```bash
mvn clean package
java -jar target/guali-tutor-1.0.0.jar
```

## Otros comandos útiles

| Acción | Con wrapper | Con Maven del sistema |
| --- | --- | --- |
| Solo compilar | `./mvnw compile` | `mvn compile` |
| Compilar y empaquetar (genera el jar) | `./mvnw package` | `mvn package` |
| Limpiar y volver a compilar desde cero | `./mvnw clean package` | `mvn clean package` |
| Ejecutar pruebas | `./mvnw test` | `mvn test` |

> En Windows, sustituye `./mvnw` por `mvnw.cmd` en cualquiera de los comandos anteriores.

## Estructura del proyecto

```
src/main/java/com/guali/tutor/       Código fuente (controlador, formulario, punto de entrada)
src/main/resources/                Hojas de estilo y contenido de las lecciones
  lecciones/leccion-01/            Markdown + imágenes de la Lección 1
pom.xml                            Configuración de Maven (dependencias y build)
mvnw, mvnw.cmd, .mvn/               Maven Wrapper
```

## Notas para quienes editan el proyecto en IntelliJ IDEA

La ventana principal (`MainForm`) fue creada con el GUI Designer de IntelliJ. Si abres el
proyecto en IntelliJ, se importa como proyecto Maven normal; solo asegúrate de que el SDK del
proyecto apunte a un JDK 25 instalado en tu máquina (IntelliJ te lo pedirá la primera vez si no
tiene uno registrado con ese nombre, y puede descargarlo por ti).

Para **editar visualmente** `MainForm.form` (arrastrar y soltar componentes en el diseñador),
IntelliJ IDEA ya no trae el GUI Designer integrado por defecto: hay que instalar el plugin
gratuito **"Swing UI Designer"** desde `Settings/Preferences → Plugins → Marketplace`. Esto solo
afecta a quien quiera modificar la ventana desde el diseñador visual — para compilar y ejecutar
el proyecto (con `mvnw`/`mvn`) el plugin no es necesario.
