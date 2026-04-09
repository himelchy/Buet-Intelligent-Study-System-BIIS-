# README

## Requirements

- JDK 21
- Maven 3.9+

Check installation:

```powershell
java -version
mvn -version
```

## Run

Open terminal in the project folder and run:

```powershell
mvn clean javafx:run
```

If you only want to build the project:

```powershell
mvn clean package
```

## Database

- No manual setup needed
- SQLite database is stored at `data/biss.db`
- Database tables and sample data are created automatically on first run

## Demo Login

Student:

```text
Roll: stu1
Password: 1234
```

Teacher:

```text
ID: TCH-1001
Password: teacher123
```

Other seeded accounts:

- Students: `stu1` to `stu50`
- Teachers: `TCH-1001` to `TCH-1007`

## Optional AI Coach Setup

AI Coach is optional. The app can run without it.

If you want AI Coach:

1. Install and start Ollama
2. Pull the default model:

```powershell
ollama pull qwen2.5:3b
```

Optional environment variables:

```powershell
$env:OLLAMA_BASE_URL="http://127.0.0.1:11434"
$env:OLLAMA_MODEL="qwen2.5:3b"
```

## Common Issue

If `mvn` is not recognized, install Maven and add it to `PATH`, then reopen the terminal.
