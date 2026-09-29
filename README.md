# Warehouse SCADA System

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![JavaFX](https://img.shields.io/badge/JavaFX-21-blue.svg)](https://openjfx.io/)
[![SQLite](https://img.shields.io/badge/SQLite-3-lightgrey.svg)](https://www.sqlite.org/)
[![Tests](https://img.shields.io/badge/Tests-53%20Passed-brightgreen.svg)]()
[![License](https://img.shields.io/badge/License-MIT-green.svg)]()

**Warehouse SCADA System** is an industrial-grade Automated Storage & Retrieval System (AS/RS) SCADA and Warehouse Management application. Built with **Java 21** and **JavaFX**, it bridges physical plant-floor automation (conveyors, crane lifters, Modbus PLC I/O) with enterprise warehouse management (inventory ledger, financial invoicing with live FX rates, and remote Telegram telemetry).

---

## Key Modules

- **SCADA Station & Automation Mimic:** Real-time 2D digital twin visualizing crane movement, conveyor infeed, and high-bay rack storage cells at 60 FPS.
- **AS/RS Infeed Pipeline:** Multi-threaded bounded buffer coordinating automated pallet putaway and bulk unload sequences.
- **Inventory Ledger:** Complete inventory management (CRUD), instant SKU search, bay occupancy mapping, and FIFO dispatch.
- **Floor Design Studio:** Interactive 2D canvas designer to build, customize, and serialize warehouse floor layouts to JSON.
- **Financial Invoicing:** Automated billing linked to dispatches with asynchronous REST API currency conversion (USD, EUR, GBP, JPY).
- **Telegram Bot Gateway:** Remote monitoring bot supporting live status pings, alerts, and remote query commands (`/status`, `/stock`, `/bays`).

---

## Architecture & Implementation Highlights

| Topic | Implementation Details | Key Files |
| :--- | :--- | :--- |
| **Advanced OOP** | Decoupled device interface contract & abstract actuator hierarchy; generic DAO with Template Method pattern. | [`IndustrialDevice.java`](src/main/java/com/example/warehousescadasystem/model/device/IndustrialDevice.java)<br>[`AbstractWarehouseActuator.java`](src/main/java/com/example/warehousescadasystem/model/device/AbstractWarehouseActuator.java)<br>[`BaseDao.java`](src/main/java/com/example/warehousescadasystem/database/BaseDao.java) |
| **Concurrency** | Producer-Consumer pipeline using a bounded buffer, explicit `ReentrantLock`, and `Condition` variables (`notFull`, `notEmpty`) managed via `ExecutorService`. | [`WarehouseBuffer.java`](src/main/java/com/example/warehousescadasystem/concurrency/WarehouseBuffer.java)<br>[`ConveyorProducerService.java`](src/main/java/com/example/warehousescadasystem/concurrency/ConveyorProducerService.java)<br>[`IntakeConsumerService.java`](src/main/java/com/example/warehousescadasystem/concurrency/IntakeConsumerService.java) |
| **Database (SQLite)** | Relational schema (`users`, `inventory`, `transactions`, `invoices`, `hardware_tags`, `app_settings`) with transactional rollbacks and runtime state persistence. | [`DatabaseManager.java`](src/main/java/com/example/warehousescadasystem/database/DatabaseManager.java)<br>[`InventoryDao.java`](src/main/java/com/example/warehousescadasystem/database/InventoryDao.java)<br>[`TransactionDao.java`](src/main/java/com/example/warehousescadasystem/database/TransactionDao.java) |
| **Networking & JSON** | Asynchronous HTTP GET requests via Java 11 `HttpClient` to FreeCurrencyAPI; Google `Gson` serialization for layouts, tags, and Telegram payloads. | [`CurrencyApiService.java`](src/main/java/com/example/warehousescadasystem/service/api/CurrencyApiService.java)<br>[`TelegramBotService.java`](src/main/java/com/example/warehousescadasystem/service/api/TelegramBotService.java) |
| **JavaFX UI Design** | Responsive dark SCADA interface using `BorderPane`, `StackPane`, `GridPane`, sortable `TableView`, `Canvas`, and collapsible sidebar rail. | [`main-app-view.fxml`](src/main/resources/com/example/warehousescadasystem/main-app-view.fxml)<br>[`login-view.fxml`](src/main/resources/com/example/warehousescadasystem/login-view.fxml) |

---

## Quick Start

### Prerequisites
- **JDK 21** or later
- **Maven 3.8+** (or included Maven wrapper `mvnw`)

### 1. Run Application
```bash
# Using Maven Wrapper (Windows PowerShell)
.\mvnw.cmd clean javafx:run

# Using standard Maven
mvn clean javafx:run
```

### 2. Default Operator Credentials
- **Username:** `admin` (or `admin@gmail.com`)
- **Password:** `Admin@123`

### 3. Run Test Suite
```bash
.\mvnw.cmd test
```
*Executes all 53 automated unit and integration tests (DAO CRUD, Modbus, Producer-Consumer, Currency API, Telegram Bot).*

---

## Project Structure

```text
warehouse-scada-system/
├── src/main/java/com/example/warehousescadasystem/
│   ├── concurrency/          # WarehouseBuffer, Producer & Consumer threads
│   ├── controller/           # JavaFX FXML controllers (Login, Main Dashboard)
│   ├── database/             # SQLite connection manager, BaseDao, entity DAOs
│   ├── model/                # Domain models, entities, and device hierarchy
│   │   └── device/           # IndustrialDevice, AbstractWarehouseActuator
│   └── service/              # Modbus TCP, AS/RS automation, REST API, Telegram
├── src/main/resources/       # FXML layout views, CSS styling, assets
├── src/test/java/            # 53 unit and integration test specifications
├── pom.xml                   # Maven dependencies & build configuration
└── README.md
```
