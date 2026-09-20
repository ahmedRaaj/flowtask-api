# Contributing to FlowTask API

Thanks for your interest in contributing! This document outlines the process for reporting issues and submitting changes.

## Getting Started

### Prerequisites

- Java 21
- Maven 3.8+ (the included Maven Wrapper is recommended)

### Setup

```bash
git clone https://github.com/ahmedRaaj/flowtask-api.git
cd flowtask-api
./mvnw spring-boot:run
```

The API starts at `http://localhost:8080`.

## How to Contribute

1. **Search existing issues** before opening a new one to avoid duplicates.
2. **Open an issue** to discuss significant changes before starting work, unless it's a small fix.
3. **Fork the repository** and create a feature branch off `main`:
   ```bash
   git checkout -b feat/short-description
   ```
4. **Make your changes**, keeping commits focused and atomic.
5. **Add or update tests** for any behavior change.
6. **Run the test suite** before submitting:
   ```bash
   ./mvnw test
   ```
7. **Push your branch** and open a pull request against `main`.

## Branch and Commit Conventions

- Use descriptive branch names: `feat/...`, `fix/...`, `docs/...`, `chore/...`.
- Write clear, imperative commit messages (e.g., `Add task completion endpoint`).
- Keep pull requests focused on a single concern; open separate PRs for unrelated changes.

## Pull Request Guidelines

- Fill in the PR description explaining the *what* and *why* of the change.
- Reference related issues (e.g., `Closes #12`).
- Ensure all tests pass and the build succeeds (`./mvnw test`).
- Be responsive to review feedback; small follow-up commits are fine.
- A maintainer will review and merge once the PR is approved and CI passes.

## Code Style

- Follow standard Java conventions and existing code formatting in the project.
- Prefer clear, self-explanatory code over excessive comments.
- Keep methods and classes focused on a single responsibility.

## Reporting Bugs

When filing a bug report, please include:

- Steps to reproduce the issue
- Expected vs. actual behavior
- Relevant logs, stack traces, or screenshots
- Environment details (OS, Java version, etc.)

## Suggesting Enhancements

Open an issue describing:

- The problem you're trying to solve
- Your proposed solution
- Any alternatives you've considered

## Code of Conduct

Be respectful and constructive in all interactions. Harassment or abusive behavior of any kind will not be tolerated.

## Questions

If you have questions, feel free to open an issue with the `question` label.
