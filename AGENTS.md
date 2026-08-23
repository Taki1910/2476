# Repository Guidelines

## Project Structure & Module Organization

The application lives in `QLCHGiay/`. Java source is organized by Spring responsibility under `src/main/java/com/example/qlchgiay/`: `controller`, `service`, `repo`, `model`, and `config`. Thymeleaf views are in `src/main/resources/templates`; browser assets are in `src/main/resources/static`. Database schema and configuration are in `src/main/resources/schema.sql` and `application.properties`. SQL Server setup and seed scripts are at the project root. Tests mirror the production packages under `src/test/java`.

## Build, Test, and Development Commands

Run commands from `QLCHGiay/`:

```text
./mvnw test                  # compile and run the test suite
./mvnw spring-boot:run      # start the application locally
./mvnw package              # build the deployable artifact
mvnw.cmd test               # Windows wrapper equivalent
```

Configure `DB_PASSWORD` before starting. The default local URL is `http://localhost:8081/login`. Initialize SQL Server with `QuanLyBanHang.sql`, then optionally run the seed scripts.

## Coding Style & Naming Conventions

Use four-space indentation and follow standard Java naming: `PascalCase` for classes, `camelCase` for methods and fields, and descriptive Vietnamese domain names consistent with existing models. Keep controllers thin, place business rules in services, and use repositories for persistence. Name Thymeleaf templates with lowercase kebab-case, such as `sanpham-form.html`. Match existing Spring and Lombok patterns before introducing new helpers.

## Testing Guidelines

Tests use Spring Boot Test and JUnit through `spring-boot-starter-test`. Name test classes after the subject with a `Test` suffix, for example `HoaDonControllerTest`. Add focused tests for validation, security, database constraints, and changed business behavior. Run the full suite with `./mvnw test`.

## Commit & Pull Request Guidelines

Commit history could not be inspected in the restricted workspace, so use short imperative messages such as `Fix invoice validation` or `Add product seed data`. Pull requests should explain the behavior changed, list test commands run, identify database or configuration changes, and include screenshots for template/UI changes.

## Security & Configuration Tips

Never commit database passwords or other secrets. Use environment variables such as `DB_PASSWORD`, `DB_URL`, `DB_USERNAME`, and `SERVER_PORT`. Preserve BCrypt handling and verify authorization behavior when changing account, employee, or settings flows.
