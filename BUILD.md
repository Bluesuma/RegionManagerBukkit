# Инструкции по сборке RegionManagerBukkit

## Требования

- **Java 17** или выше
- **Maven 3.6+**
- **Git**

## Быстрая сборка

### 1. Клонирование репозитория

```bash
git clone https://github.com/your-repo/RegionManagerBukkit.git
cd RegionManagerBukkit
```

### 2. Сборка плагина

```bash
mvn clean package
```

После успешной сборки файл плагина будет находиться в `target/RegionManagerBukkit-1.0.0.jar`

### 3. Установка на сервер

```bash
# Скопируйте JAR файл в папку plugins
cp target/RegionManagerBukkit-1.0.0.jar /path/to/your/server/plugins/
```

## Детальная сборка

### Настройка окружения

1. **Установите Java 17+**
   ```bash
   # Ubuntu/Debian
   sudo apt update
   sudo apt install openjdk-17-jdk
   
   # CentOS/RHEL
   sudo yum install java-17-openjdk-devel
   
   # macOS
   brew install openjdk@17
   ```

2. **Установите Maven**
   ```bash
   # Ubuntu/Debian
   sudo apt install maven
   
   # CentOS/RHEL
   sudo yum install maven
   
   # macOS
   brew install maven
   ```

3. **Проверьте версии**
   ```bash
   java -version
   mvn -version
   ```

### Сборка с тестами

```bash
# Сборка с запуском тестов
mvn clean package

# Сборка без тестов (быстрее)
mvn clean package -DskipTests

# Сборка с подробным выводом
mvn clean package -X
```

### Сборка для разработки

```bash
# Установка в локальный репозиторий
mvn clean install

# Сборка с отладочной информацией
mvn clean package -Dmaven.compiler.debug=true
```

## Структура сборки

После сборки в папке `target/` будут созданы следующие файлы:

```
target/
├── RegionManagerBukkit-1.0.0.jar          # Основной JAR файл
├── RegionManagerBukkit-1.0.0-sources.jar  # Исходный код
├── RegionManagerBukkit-1.0.0-javadoc.jar  # Документация
├── classes/                               # Скомпилированные классы
├── generated-sources/                     # Сгенерированные источники
├── maven-archiver/                        # Архив Maven
└── surefire-reports/                      # Отчеты тестов
```

## Тестирование

### Запуск тестов

```bash
# Запуск всех тестов
mvn test

# Запуск конкретного теста
mvn test -Dtest=RegionManagerTest

# Запуск тестов с подробным выводом
mvn test -X
```

### Покрытие кода

```bash
# Генерация отчета о покрытии
mvn clean test jacoco:report

# Отчет будет в target/site/jacoco/index.html
```

## Развертывание

### Локальное тестирование

1. **Создайте тестовый сервер**
   ```bash
   # Скачайте PaperMC
   wget https://api.papermc.io/v2/projects/paper/versions/1.20.4/builds/445/downloads/paper-1.20.4-445.jar
   
   # Создайте папку сервера
   mkdir test-server
   cd test-server
   
   # Скопируйте JAR
   cp ../paper-1.20.4-445.jar paper.jar
   
   # Создайте скрипт запуска
   echo 'java -Xmx2G -Xms1G -jar paper.jar nogui' > start.sh
   chmod +x start.sh
   ```

2. **Установите плагин**
   ```bash
   # Создайте папку plugins
   mkdir plugins
   
   # Скопируйте плагин
   cp ../../target/RegionManagerBukkit-1.0.0.jar plugins/
   ```

3. **Запустите сервер**
   ```bash
   ./start.sh
   ```

### Продакшн развертывание

1. **Подготовьте сервер**
   ```bash
   # Создайте папку для плагинов
   mkdir -p /opt/minecraft/plugins
   
   # Скопируйте плагин
   cp target/RegionManagerBukkit-1.0.0.jar /opt/minecraft/plugins/
   
   # Установите права
   chown minecraft:minecraft /opt/minecraft/plugins/RegionManagerBukkit-1.0.0.jar
   chmod 644 /opt/minecraft/plugins/RegionManagerBukkit-1.0.0.jar
   ```

2. **Настройте конфигурацию**
   ```bash
   # Запустите сервер один раз для создания конфигурации
   cd /opt/minecraft
   java -Xmx4G -Xms2G -jar paper.jar nogui
   
   # Остановите сервер и настройте config.yml
   nano plugins/RegionManagerBukkit/config.yml
   ```

## CI/CD

### GitHub Actions

Создайте файл `.github/workflows/build.yml`:

```yaml
name: Build RegionManagerBukkit

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Cache Maven packages
      uses: actions/cache@v3
      with:
        path: ~/.m2
        key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
        restore-keys: ${{ runner.os }}-m2
    
    - name: Build with Maven
      run: mvn clean package
    
    - name: Upload artifact
      uses: actions/upload-artifact@v3
      with:
        name: RegionManagerBukkit
        path: target/RegionManagerBukkit-*.jar
```

### Jenkins

Создайте `Jenkinsfile`:

```groovy
pipeline {
    agent any
    
    tools {
        maven 'Maven 3.8.6'
        jdk 'JDK 17'
    }
    
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        
        stage('Build') {
            steps {
                sh 'mvn clean package'
            }
        }
        
        stage('Test') {
            steps {
                sh 'mvn test'
            }
        }
        
        stage('Archive') {
            steps {
                archiveArtifacts artifacts: 'target/RegionManagerBukkit-*.jar', fingerprint: true
            }
        }
    }
    
    post {
        always {
            junit '**/target/surefire-reports/*.xml'
        }
    }
}
```

## Устранение проблем

### Ошибки сборки

#### "Java version not supported"
```bash
# Проверьте версию Java
java -version

# Установите Java 17
sudo apt install openjdk-17-jdk
```

#### "Maven not found"
```bash
# Установите Maven
sudo apt install maven

# Или скачайте вручную
wget https://archive.apache.org/dist/maven/maven-3/3.8.6/binaries/apache-maven-3.8.6-bin.tar.gz
tar -xzf apache-maven-3.8.6-bin.tar.gz
export PATH=$PATH:./apache-maven-3.8.6/bin
```

#### "Dependencies not found"
```bash
# Очистите кэш Maven
mvn dependency:purge-local-repository

# Пересоберите
mvn clean package
```

### Проблемы с тестами

#### Тесты не проходят
```bash
# Запустите тесты с подробным выводом
mvn test -X

# Проверьте логи тестов
cat target/surefire-reports/*.txt
```

#### Проблемы с памятью
```bash
# Увеличьте память для Maven
export MAVEN_OPTS="-Xmx2G -Xms1G"
mvn clean package
```

## Дополнительные команды

### Очистка

```bash
# Очистка всех сгенерированных файлов
mvn clean

# Очистка кэша зависимостей
mvn dependency:purge-local-repository
```

### Документация

```bash
# Генерация Javadoc
mvn javadoc:javadoc

# Генерация сайта
mvn site
```

### Анализ кода

```bash
# Проверка стиля кода
mvn checkstyle:check

# Анализ зависимостей
mvn dependency:analyze
```

---

**Примечание**: Убедитесь, что у вас установлены все необходимые зависимости и правильные версии Java и Maven перед началом сборки. 