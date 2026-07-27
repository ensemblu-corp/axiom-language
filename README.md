# Axiom Language

The `axiom-language` engine acts as the **Grammar and Policy Enforcer** for the Axiom ecosystem. It provides the mechanisms to define structural schemata and perform rigorous, deep-traversal validation of data against those policies.

## 🏛️ Integration

Summon the Language processor into your project:

**Maven**

```xml
<dependency>
    <groupId>com.ensemblu</groupId>
    <artifactId>axiom-language</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Gradle**

```groovy
implementation("com.ensemblu:axiom-language:1.0.0")
```

## ⚖️ Sovereign Law

This JAR enforces the structural integrity of your data.

-   **Schema-Policy-Oriented**: All data processing is bound to an `.axiom` schema definition.

-   **Strict Mode**: When enabled, the `MasterGuard` enforces complete structural enclosure, rejecting any unknown fields.

-   **Handshake Protocol**: Validation is treated as a secure "Handshake" between raw data and the schema registry.


## ⚡ Operational Entry

The `SchemaGuard` serves as the registry and primary validator.

### 1. Validating Content

```java
import com.ensemblu.axiom.schema.SchemaGuard;

// Executes the validation handshake
final var result = SchemaGuard.checkContent(rawJsonString)
    .basedOnSchemaName("user-profile")
    .withAxiomParser();
```

### 2. Registry Management

The engine maintains a `ConcurrentHashMap` of parsed schemata to ensure high-performance structural lookups.

-   `SchemaGuard.reloadAll()`: Purges the entire registry.

-   `SchemaGuard.reload("schema-name")`: Refreshes a specific policy definition.


## 🛡️ Structural Guardrails

-   **Max Depth**: `MasterGuard` implements a hard limit of **32 levels** of nesting to prevent structural stack overflow.

-   **Validation Pipeline**: Uses the `If` declarative flow to ensure that constraints (min/max, patterns, list sizing) are evaluated as immutable contracts, not procedural checks.


## 📜 Legal

This project is governed by the principles of immutable software architecture. See `LICENSE.md` for the specific terms of use.
