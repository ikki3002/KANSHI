# Parallel UI-Driven Demonstration Plan
**Project Title:** KANSHI — Industrial Warehouse SCADA & Management System  
**Format:** Screen-by-Screen Walkthrough (Demonstrating code architecture & syllabus requirements directly alongside live UI actions).

---

## Strategy Summary
Instead of listing abstract theory one by one, you walk your teacher through the **live application stations**. On each screen, you highlight the exact **Syllabus Requirements**, **OOP Concepts**, **Concurrency**, and **Database logic** powering that specific feature, referencing the exact files in your codebase.

---

## Station 1: Login & Navigation Shell (JavaFX Architecture & Security)
### What to Show on Screen:
- Open application on `login-view.fxml`. Show `PasswordField`, sign-in validation, then enter into `main-app-view.fxml`.
- Resize the main application window (showing dynamic responsiveness). Click the sidebar toggle `[<]` and `[>]` to collapse and expand the navigation rail.

### Syllabus Topics Covered Here:
1. **JavaFX Layout Panes & Controls**:
   - `BorderPane` (root frame), `StackPane` (multi-view switching), `GridPane` (metrics alignment), `PasswordField` (masked credentials).
2. **Layout Responsiveness**:
   - `AnchorPane` constraints, `Priority.ALWAYS` / `HBox.hgrow`, dynamic canvas recalculation on resize.
3. **Database Authentication & DAO Architecture**:
   - Authenticating user credentials against SQLite `users` table.

### Exact Files & Locations:
- **FXML UI**:
  - `src/main/resources/com/example/warehousescadasystem/login-view.fxml`
  - `src/main/resources/com/example/warehousescadasystem/main-app-view.fxml`
- **Controller**:
  - `src/main/java/com/example/warehousescadasystem/controller/LoginController.java`
  - `src/main/java/com/example/warehousescadasystem/controller/MainAppController.java`
- **Database Model & DAO**:
  - `src/main/java/com/example/warehousescadasystem/database/UserDao.java` (Extends `BaseDao<User>`)
  - `src/main/java/com/example/warehousescadasystem/model/User.java` (Encapsulation with hashed password)

### What to Say to Your Teacher:
> *"Here on the login and shell, we implemented a responsive JavaFX structure using `BorderPane` and `StackPane`. All form inputs use secure `PasswordField` controls, and authentication queries our SQLite `users` table through `UserDao`, which extends our generic abstract `BaseDao`. As you can see when I resize the window or toggle the sidebar, the layout is fully responsive."*

---

## Station 2: SCADA Station (Advanced OOP & Concurrency Pipeline)
### What to Show on Screen:
- Go to **SCADA Station** (`paneHome` / `paneScada`).
- Show the animated 2D AS/RS crane and conveyor graphic.
- Click **Start Pipeline** or simulate infeed arrival. Show pallets moving from the intake conveyor buffer into high-bay racks.

### Syllabus Topics Covered Here:
1. **Advanced OOP (Interfaces & Abstract Classes)**:
   - Hardware equipment is abstracted into an industrial device hierarchy:
     - `IndustrialDevice` (Common Interface)
     - `AbstractWarehouseActuator` (Abstract Class holding common actuator state: `deviceId`, `name`, `running`, and abstract methods `start()`/`stop()`)
     - `BeltConveyorDevice` (Concrete Subclass extending actuator)
     - `OpticalSensorDevice` (Concrete Subclass implementing device interface)
2. **Concurrency & Thread Pools (Producer-Consumer Problem)**:
   - Conveyor belt pushes pallets into a thread-safe bounded buffer; the AS/RS crane worker retrieves them.
   - Solved using `ReentrantLock` and two explicit `Condition` variables (`notFull`, `notEmpty`).
   - Managed by an `ExecutorService` thread pool and dispatched safely to UI via `Platform.runLater()`.

### Exact Files & Locations:
- **Advanced OOP (Interfaces & Abstract Classes)**:
  - `src/main/java/com/example/warehousescadasystem/model/device/IndustrialDevice.java` (`public interface IndustrialDevice`)
  - `src/main/java/com/example/warehousescadasystem/model/device/AbstractWarehouseActuator.java` (`public abstract class AbstractWarehouseActuator implements IndustrialDevice`)
  - `src/main/java/com/example/warehousescadasystem/model/device/BeltConveyorDevice.java` (`public class BeltConveyorDevice extends AbstractWarehouseActuator`)
  - `src/main/java/com/example/warehousescadasystem/model/device/OpticalSensorDevice.java` (`public class OpticalSensorDevice implements IndustrialDevice`)
- **Concurrency & Producer-Consumer**:
  - `src/main/java/com/example/warehousescadasystem/concurrency/WarehouseBuffer.java` (`ReentrantLock`, `Condition notFull`, `Condition notEmpty`, `@FunctionalInterface BufferChangeListener`)
  - `src/main/java/com/example/warehousescadasystem/concurrency/ConveyorProducerService.java`
  - `src/main/java/com/example/warehousescadasystem/concurrency/IntakeConsumerService.java`

### What to Say to Your Teacher:
> *"In this SCADA station, two major syllabus topics run in parallel. First, for **Advanced OOP**, all machines follow a strict hierarchy: we have an `IndustrialDevice` interface, an `AbstractWarehouseActuator` class that defines abstract `start()` and `stop()` methods, and concrete implementations like `BeltConveyorDevice`. Second, for **Concurrency**, we solved the classical Producer-Consumer problem using `WarehouseBuffer.java`. It coordinates background conveyor threads and crane workers using a `ReentrantLock` with `notFull` and `notEmpty` conditions, keeping the JavaFX UI completely smooth."*

---

## Station 3: Inventory Ledger (Full CRUD & Database Template Method)
### What to Show on Screen:
- Switch to **Inventory Ledger** (`paneInventory`).
- Perform live **CRUD**:
  - **Create:** Click *Add Product*, enter SKU `SKU-PROMO-01`, Name `Hydraulic Valve`, Qty `40`, Bay `Bay-12`. Save.
  - **Read:** Type `HYDRAULIC` in the instant search box to filter.
  - **Update:** Edit quantity to `65`.
  - **Delete:** Delete the test item and confirm removal.

### Syllabus Topics Covered Here:
1. **Full CRUD Operations**: Complete Create, Read, Update, Delete cycle.
2. **Database Integration & Relational Design**:
   - SQLite table `inventory` storing records with automatic timestamps.
3. **Template Method Pattern & Generic DAOs (OOP)**:
   - `CrudDao<T>` (Generic interface for CRUD contracts)
   - `BaseDao<T>` (Abstract class providing generic connection handling and abstract `mapResultSet(ResultSet rs)` template method)
   - `InventoryDao` (Concrete subclass extending `BaseDao<Product>`)

### Exact Files & Locations:
- **Generic DAO Interface & Abstract Base**:
  - `src/main/java/com/example/warehousescadasystem/database/CrudDao.java` (`public interface CrudDao<T>`)
  - `src/main/java/com/example/warehousescadasystem/database/BaseDao.java` (`public abstract class BaseDao<T> implements CrudDao<T>`)
- **Concrete Subclass & Entity**:
  - `src/main/java/com/example/warehousescadasystem/database/InventoryDao.java` (`public class InventoryDao extends BaseDao<Product>`)
  - `src/main/java/com/example/warehousescadasystem/model/Product.java`
- **Database Schema**:
  - `src/main/java/com/example/warehousescadasystem/database/DatabaseManager.java` (table `inventory`)

### What to Say to Your Teacher:
> *"In our Inventory module, we demonstrate complete CRUD data manipulation. When I add, filter, update, or delete items, the operations execute through our Data Access Layer. Here we implemented the Template Method design pattern: `CrudDao<T>` is our generic interface, `BaseDao<T>` is an abstract class managing connection lifecycles, and `InventoryDao` extends it to map SQLite rows into strongly-typed `Product` objects."*

---

## Station 4: Floor Layout Studio (Interactive Canvas & JSON I/O)
### What to Show on Screen:
- Switch to **Floor Designer** (`paneFloorDesign`).
- Click on cell tools (`CONVEYOR`, `RACK`, `ROBOT`, `CLEAR`) and paint a custom warehouse zone on the grid.
- Click **Export JSON** or show layout saving.

### Syllabus Topics Covered Here:
1. **Interactive JavaFX Canvas**:
   - Direct mouse event handling (`onMouseClicked`, `onMouseDragged`) on a dynamic canvas.
2. **OOP Encapsulation & Enums**:
   - `FloorCellPlacement` encapsulating `(row, col, FloorElementType)`.
   - Strongly-typed `FloorElementType` enum.
3. **JSON Serialization & File I/O (Week 7 Syllabus)**:
   - Parsing and writing structured JSON floor configurations using Google `Gson`.

### Exact Files & Locations:
- **Service & File Persistence**:
  - `src/main/java/com/example/warehousescadasystem/service/layout/FloorLayoutService.java` (Saves and loads layout to JSON)
- **Domain Model & Enums**:
  - `src/main/java/com/example/warehousescadasystem/model/FloorCellPlacement.java`
  - `src/main/java/com/example/warehousescadasystem/model/FloorElementType.java`

### What to Say to Your Teacher:
> *"Here in the 2D Floor Studio, we showcase advanced JavaFX Canvas controls and Week 7 JSON file persistence. Operators can place conveyors and racks interactively. Under the hood, `FloorLayoutService.java` serializes these grid coordinate objects into structured JSON using Google Gson and reloads them on demand."*

---

## Station 5: Financial Invoicing (Networking & HTTP JSON Parsing)
### What to Show on Screen:
- Switch to **Financial Invoicing** (`paneInvoicing`).
- Click **Live Currency Conversion** (convert USD inventory valuation to EUR and GBP). Show the updated converted totals.

### Syllabus Topics Covered Here:
1. **Networking & HTTP Requests**:
   - Making live asynchronous HTTP GET requests over the internet using Java 11+ `java.net.http.HttpClient`.
2. **Data Parsing (JSON REST API)**:
   - Receiving raw JSON payload from an external currency API and parsing it using Google `Gson`.
3. **Relational Database Link**:
   - Table `invoices` with foreign-key relationships to dispatched inventory items.

### Exact Files & Locations:
- **HTTP Networking & JSON Parsing**:
  - `src/main/java/com/example/warehousescadasystem/service/api/CurrencyApiService.java` (`HttpClient`, `HttpRequest`, `Gson.fromJson()`)
- **Database Model & DAO**:
  - `src/main/java/com/example/warehousescadasystem/database/InvoiceDao.java` (Extends `BaseDao<Invoice>`)
  - `src/main/java/com/example/warehousescadasystem/model/Invoice.java`

### What to Say to Your Teacher:
> *"In the Financial Invoicing module, we demonstrate external Networking and JSON Parsing. In `CurrencyApiService.java`, we use Java's modern `HttpClient` to send asynchronous GET requests to a live currency REST API. We parse the incoming JSON string with Google Gson to dynamically convert our warehouse asset valuations into Euros and Pounds with real-time exchange rates."*

---

## Station 6: Telegram Bot & System Persistence
### What to Show on Screen:
- Switch to **Telegram Bot** in the left sidebar (`paneTelegram`).
- Show the saved Bot Token and Chat ID.
- Click **Send Test Ping** and show the live communication log console.

### Syllabus Topics Covered Here:
1. **External API Communication**:
   - Sending HTTPS messages and polling remote commands (`/stock`, `/status`, `/bays`) via Telegram Bot API.
2. **Relational Persistence for Configuration & Hardware Tags**:
   - SQLite `app_settings` (saves credentials across restarts via `SettingsDao`).
   - SQLite `hardware_tags` (persists live Modbus tag register values and active states via reactive listeners in `TagManager`).
   - SQLite `transactions` (persists all historical operational audits).

### Exact Files & Locations:
- **Telegram Bot Integration**:
  - `src/main/java/com/example/warehousescadasystem/service/api/TelegramBotService.java`
- **Database Persistence & DAOs**:
  - `src/main/java/com/example/warehousescadasystem/database/SettingsDao.java` (table `app_settings`)
  - `src/main/java/com/example/warehousescadasystem/database/TransactionDao.java` (table `transactions`)
  - `src/main/java/com/example/warehousescadasystem/service/modbus/TagManager.java` (table `hardware_tags`)
  - `src/main/java/com/example/warehousescadasystem/model/ModbusTag.java` (Reactive state change listener)

### What to Say to Your Teacher:
> *"Our dedicated Telegram Bot module connects our warehouse to mobile operators. It communicates with Telegram's HTTPS API. Notice that the Bot Token and Chat ID are remembered even after restarting the application. We achieved this by building a dedicated `SettingsDao` backed by SQLite. In addition, our `TagManager` and `TransactionDao` persist every tag state change and audit log into database tables so no operational data is ever lost."*

---

## Station 7: GitHub Version Control Verification (Browser)
### What to Show on Screen:
- Open your browser showing the GitHub repository.
- Navigate to the **Commits** tab. Scroll through the timeline from **September 6th** to **September 28th**.

### Syllabus Topics Covered Here:
1. **Continuous Version Control**:
   - Consistent, incremental commits starting from idea submission.
   - Feature branching and descriptive engineering commit messages.

### Exact Location:
- GitHub Repository web URL.

### What to Say to Your Teacher:
> *"Finally, here is our GitHub commit log. Starting from our initial project idea on September 6th all the way to completion today, September 28th, we practiced disciplined version control with meaningful commits, tracking every milestone systematically."*

---

## Quick Reference Summary Table for Q&A

| Station / UI View | Syllabus Topic | Key Concept | Exact Project File |
| :--- | :--- | :--- | :--- |
| **Login / Shell** | JavaFX Design & Responsiveness | BorderPane, StackPane, PasswordField, dynamic constraints | `login-view.fxml`, `main-app-view.fxml` |
| **SCADA Mimic** | Advanced OOP | Interface & Abstract Class hierarchy | `IndustrialDevice.java`, `AbstractWarehouseActuator.java`, `BeltConveyorDevice.java` |
| **SCADA Mimic** | Concurrency | Producer-Consumer with ReentrantLock & Conditions | `WarehouseBuffer.java`, `ConveyorProducerService.java`, `IntakeConsumerService.java` |
| **Inventory Ledger** | Data Manipulation (CRUD) | Add, Search, Edit, Delete with TableView | `InventoryDao.java`, `Product.java` |
| **Inventory Ledger** | Database Integration | Template Method Pattern & Generic Base DAO | `CrudDao.java`, `BaseDao.java`, `DatabaseManager.java` |
| **Floor Studio** | Canvas & File I/O | Dynamic 2D grid painting, JSON layout persistence | `FloorLayoutService.java`, `FloorCellPlacement.java` |
| **Financial Invoicing**| Networking & Parsing | HTTP GET with `HttpClient` & JSON parsing with `Gson` | `CurrencyApiService.java`, `InvoiceDao.java` |
| **Telegram Bot** | API & Persistence | Telegram HTTPS API, SQLite settings & tag persistence | `TelegramBotService.java`, `SettingsDao.java`, `TagManager.java` |
| **Browser** | Version Control | Continuous Git commits starting Sept 6th | GitHub Repository Commits History |
