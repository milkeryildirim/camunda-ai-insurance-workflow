# Camunda AI Insurance Workflow

## Project Overview

This project demonstrates how to transform traditional USER Tasks in Camunda workflows into AI-automated processes. It's a comprehensive test project that explores the automation of user interactions through AI integration in an insurance workflow context.

The project consists of two main components:
1. **Camunda Platform 7.24.0** - The workflow engine with auto-deployed insurance processes
2. **Insurance Workers** - Spring Boot application providing external task workers

## Versioning Strategy

The project follows a specific versioning pattern to distinguish between different automation levels:

- **v.X.X.X**: Traditional versions that require user interaction (USER Tasks)
- **v.X.X.X-ai**: AI-automated versions where user responsibilities are handled by AI systems

## Architecture

### Camunda 7 Environment

The project includes a Docker Compose setup for running Camunda Platform 7.24.0 in a local development environment. This setup uses an embedded H2 database and is configured with **automatic resource deployment** for seamless development workflow.

### External Task Workers

The `insurance-workers/` directory contains a Spring Boot application that provides external task workers for processing business logic outside of the Camunda engine. This follows the External Task pattern for better scalability and technology diversity.

### Technology Stack

- **Camunda Platform 7.24.0**: Workflow and decision automation platform
- **Spring Boot 3.5.7**: Java application framework for building external task workers
- **Camunda External Task Client**: For connecting workers to the Camunda engine
- **Spring AI**: Integration framework for AI capabilities (planned)
- **H2 Database**: Embedded database for local development
- **Docker & Docker Compose**: Containerization and orchestration
- **Maven**: Build and dependency management

## Getting Started

### Prerequisites

- Docker and Docker Compose
- Java 21
- Maven 3.6 or later
- Git

### Quick Start

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd camunda-ai-insurance-workflow
   ```

2. **Start Camunda Platform**
   ```bash
   docker-compose up -d
   ```

3. **Build and start External Workers**
   ```bash
   cd insurance-workers
   mvn clean install
   mvn spring-boot:run
   ```

4. **Access the applications**
   - **Camunda Platform**: `http://localhost:8080/camunda`
   - **External Workers API**: `http://localhost:8080` (Spring Boot default port)

### Configuration

#### Camunda External Task Client

The external workers are configured to connect to Camunda via:
- **Base URL**: `http://localhost:8080/engine-rest`
- **Worker ID**: `insurance-worker`
- **Lock Duration**: 30 seconds

Configuration can be modified in `insurance-workers/src/main/resources/application.properties`:
```properties
camunda.bpm.client.base-url=http://localhost:8080/engine-rest
camunda.bpm.client.worker-id=insurance-worker
camunda.bpm.client.lock-duration=30000
```

## Project Structure

```
├── README.md
├── docker-compose.yaml          # Camunda 7 Platform setup
├── camunda-resources/           # Auto-deployed resources
│   ├── bpmn/                   # BPMN workflow definitions
│   │   └── User-Insurance.bpmn
│   └── forms/                  # Camunda form definitions
│       ├── check_invoice.form
│       ├── claim_registration.form
│       └── payment_decision.form
└── insurance-workers/          # Spring Boot External Task Workers
    ├── src/main/java/
    │   └── tech/yildirim/camunda/insurance/workers/
    │       ├── InsuranceWorkersApplication.java
    │       ├── common/         # Common interfaces and base classes
    │       │   ├── CamundaWorker.java
    │       │   └── AbstractCamundaWorker.java
    │       ├── config/         # Spring configuration
    │       │   └── ExternalTaskClientConfiguration.java
    │       ├── policy/         # Policy-related workers
    │       │   └── PolicyValidationWorker.java
    │       └── adjuster/       # Adjuster-related workers
    │           └── AdjusterAssignmentWorker.java
    ├── pom.xml
    └── target/
```

## Available Services

### Camunda Platform Services

- **Camunda Cockpit**: `http://localhost:8080/camunda/app/cockpit/` - Process monitoring and operations
- **Camunda Tasklist**: `http://localhost:8080/camunda/app/tasklist/` - Task management interface  
- **Camunda Admin**: `http://localhost:8080/camunda/app/admin/` - User and group management
- **REST API**: `http://localhost:8080/engine-rest/` - Camunda REST API endpoint

### External Task Workers

The Spring Boot application provides the following workers:

| Worker | Topic | Description |
|--------|--------|-------------|
| `PolicyValidationWorker` | `policy-validation` | Validates insurance policy eligibility |
| `AdjusterAssignmentWorker` | `adjuster-assignment` | Assigns insurance adjusters to claims |

## Configuration Features

### Camunda Platform

- **Automatic Resource Deployment**: All BPMN and form files are auto-deployed from `camunda-resources/`
- **Demo Applications Disabled**: Clean environment without example processes
- **CSRF Protection Disabled**: Easier REST API testing (development only)
- **H2 Embedded Database**: No external database required
- **External Task Support**: Ready for Spring Boot external task workers

### Insurance Workers Application

- **Auto-discovery**: Automatically finds and registers all `CamundaWorker` implementations
- **Resilient Error Handling**: Comprehensive error handling with retries
- **Configurable Parameters**: Customizable connection settings and timeouts
- **Structured Logging**: Detailed logging for monitoring and debugging

## Development Workflow

### Adding New Processes

1. Place your `.bpmn` files in `camunda-resources/bpmn/`
2. Place your `.form` files in `camunda-resources/forms/`
3. Restart Camunda: `docker-compose restart`
4. Resources will be automatically deployed

### Adding New External Task Workers

1. Create a new worker class implementing `CamundaWorker` or extending `AbstractCamundaWorker`
2. Annotate with `@Component`
3. Implement the `getTopicName()` and business logic methods
4. Restart the Spring Boot application

Example:
```java
@Component
public class MyCustomWorker extends AbstractCamundaWorker {
    @Override
    public String getTopicName() {
        return "my-custom-topic";
    }
    
    @Override
    protected void executeBusinessLogic(ExternalTask externalTask, 
                                      ExternalTaskService externalTaskService) {
        // Your business logic here
    }
}
```


## Troubleshooting

### Common Issues

**Connection Refused**: Ensure Camunda Platform is running before starting workers:
```bash
docker-compose ps  # Check if Camunda is running
docker-compose logs -f  # Check Camunda logs
```

**Process Not Found**: Verify BPMN files are properly deployed:
- Check Camunda Cockpit deployments
- Restart docker-compose if needed
- Verify file syntax with Camunda Modeler

## Contributing

This is a test project for educational and demonstration purposes. Contributions are welcome:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request
