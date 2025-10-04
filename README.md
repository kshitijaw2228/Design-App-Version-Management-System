# Design-App-Version-Management-System

Overview

This project is a simple simulation of how an app store backend manages app versions and rollouts.
It handles version uploads, diff patch creation, staged releases, and simulated device updates — all implemented using plain Java, without any external frameworks or databases.

## Project Structure

| Package     | Purpose                                                                 |
| ----------- | ----------------------------------------------------------------------- |
| `domain`    | Core entities such as `AppVersion`, `Device`, and `UpdatePlan`.         |
| `service`   | Core logic for uploads, diff creation, installation, and orchestration. |
| `rollout`   | Rollout control logic (e.g., Beta rollout).                             |
| `store`     | In-memory data store for versions and rollout information.              |
| `util`      | Utility helpers like version comparison.                                |
| `Main.java` | Entry point that runs multiple scenarios and test cases.                |



## Functional Requirements

1. **Upload App Versions**

- The system allows uploading new app versions along with metadata (version name, minimum Android version, description).
- Stores APK content in an in-memory file system (FileService).
- Prevents duplicate version uploads.

2. **Create Diff Patches**

- Generates binary diff packs between two app versions (DiffService).
- Supports reusing existing diffs if already generated.
- Validates presence of both source and target APKs.

3. **Release Versions**

- Marks a specific version as released with an associated rollout strategy.
- Supports beta releases through BetaRolloutStrategy (whitelisted devices).

4. **Check for Updates**

- Devices can check whether a newer eligible version is available.
- Filters out versions not released or unsupported due to Android version restrictions.
- Generates UpdatePlan (install/update) dynamically for eligible devices.

5. **Execute Update or Install**

- Executes updates or installations based on the plan (InstallationService).
- Updates the device’s current version state after success.

6. **Handle Race Conditions**

- Concurrent update requests are synchronized per device to prevent inconsistent states.
- Includes a test simulation (Device-RACE) to verify thread safety.


## Non-Functional Requirements
- Thread Safety
- Extensibility
- Maintainability
- Observability
- Reliability

## How to Run

1. Clone the project
2. Open it in IntelliJ IDEA (or any Java IDE)
3. Make sure Java 17 or higher is configured
4. Run org.phonepe.Main
5. Check the console output — each test demonstrates a real use case


## Design Highlights

1. **Object-Oriented Design:** Built around core OOP principles — encapsulation, abstraction, polymorphism, and immutability.

2. **Clean Architecture:** Each class has a single responsibility and clear boundaries between domain, service, rollout, and store layers

3. **Design Patterns:**

- Strategy Pattern — flexible rollout logic (RolloutStrategy, BetaRolloutStrategy).
- Facade Pattern — unified orchestration through VersionManager.

4. **Thread Safety:** Uses ConcurrentHashMap and synchronized device locks for safe concurrent updates.

5. **Extensibility:** Modules are loosely coupled and easy to extend (e.g., new rollout strategies can be added).

6. **Transparency:** Structured console logs show every key operation and decision flow.

7. **In-Memory Architecture:** AppStore and FileService use ConcurrentHashMap for storage.

## Future Improvements

1. Add more rollout strategies (percentage-based)
2. Persist app data in a database or JSON file
3. Add rollback and retry mechanisms

