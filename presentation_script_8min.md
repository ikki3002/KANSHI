# 🎙️ Kanshi WMS — 8-Minute Video Presentation Script & Walkthrough Notes

> **Project Name:** Kanshi Warehouse Management System (IT/OT Convergence)  
> **Target Duration:** Exactly 8 minutes (00:00 – 08:00)  
> **Technologies:** Java 21, JavaFX, SQLite (JDBC), Modbus TCP/IP (Factory I/O), REST API (Frankfurter / European Central Bank), Gson, Concurrency (Locks/Conditions/Thread Pools).

---

## ⏱️ Video Presentation Timeline Summary

| Time | Topic | Key Code / Artifact to Show |
| :--- | :--- | :--- |
| **00:00 – 00:45** | **Introduction & Architecture Overview** | App Login & System Launch (`HelloApplication.java`) |
| **00:45 – 01:30** | **1. Version Control & GitHub Repository** | Git commit log / GitHub insights graph |
| **01:30 – 02:30** | **2. Advanced OOP Concepts** | `CrudDao<T>`, `IndustrialDevice`, `AbstractWarehouseActuator` |
| **02:30 – 03:30** | **3. JavaFX UI Design & Control Showcase** | `main-app-view.fxml`, `BorderPane`, `StackPane`, `Canvas` |
| **03:30 – 04:15** | **4. Layout Responsiveness & Window Binding** | Resizing application window live; property bindings |
| **04:15 – 05:15** | **5. Concurrency & Multi-Threading** | `WarehouseBuffer.java` (Producer-Consumer, Locks/Conditions) |
| **05:15 – 06:15** | **6. Database Integration & Foreign Keys** | `DatabaseManager.java`, SQLite schema & FK relationships |
| **06:15 – 07:00** | **7. Live Data Manipulation (Full CRUD)** | Inventory Ledger tab (Create, Read, Update, Delete) |
| **07:00 – 07:45** | **8. Networking & JSON REST API Parsing** | `CurrencyApiService.java`, live currency selector & ticker |
| **07:45 – 08:00** | **Conclusion & Wrap-Up** | Final system summary & Q&A readiness |

---

## 🎬 Section-by-Section Script & Visual Guide

### 📍 [00:00 – 00:45] Introduction & Architecture Overview (45s)
**🖥️ What to show on screen:**
- Start on desktop / IDE. Launch `HelloApplication.java`.
- Show the clean JavaFX Login dialog, enter `admin@gmail.com` / `Admin@123`, and log into the main dashboard.

**🗣️ Spoken Script:**
> *"Hello everyone and welcome. Today I am presenting **Kanshi Warehouse Management System**, an enterprise-grade desktop application built with Java 21 and JavaFX.*  
>  
> *Kanshi bridges the critical gap between enterprise Information Technology (IT)—such as inventory relational databases, commercial invoicing, and live financial REST APIs—and physical Operational Technology (OT)—such as industrial Modbus PLC automation and automated storage and retrieval cranes.*  
>  
> *In this 8-minute presentation, I will walk you through all core computer science requirements: our Git version control history, advanced object-oriented design patterns, rich JavaFX layout responsiveness, custom multi-threaded concurrency, SQLite relational database structure, full CRUD data manipulation, and asynchronous REST API networking."*

---

### 📍 [00:45 – 01:30] Point 1: Version Control & GitHub Commits (45s)
**🖥️ What to show on screen:**
- Switch to terminal and run: `git log --oneline --graph -n 15` or open your GitHub repository commit history graph in your browser.
- Highlight the steady progression of meaningful commit messages starting from project inception in early September through to the final build.

**🗣️ Spoken Script:**
> *"First, let's examine our **Version Control**. From the project's inception following idea submission, we maintained disciplined, modular Git version control on GitHub.*  
>  
> *As shown in our commit history, each phase of development was committed incrementally—starting from foundational login authentication and SQLite database schemas, progressing to Modbus TCP industrial drivers, Producer-Consumer concurrency buffers, custom 2D SCADA graphics, and culminating in live REST API financial integrations.*  
>  
> *Every feature branch was validated with automated test suites before being merged, ensuring a clean, bisectable commit log with descriptive semantic commit messages."*

---

### 📍 [01:30 – 02:30] Point 2: Advanced OOP Concepts (60s)
**🖥️ What to show on screen:**
- Open IDE:
  1. `CrudDao.java` (`src/main/java/.../database/CrudDao.java`)
  2. `IndustrialDevice.java` (`.../model/device/IndustrialDevice.java`)
  3. `AbstractWarehouseActuator.java` & `BeltConveyorDevice.java`

**🗣️ Spoken Script:**
> *"Next, let's look at **Advanced Object-Oriented Programming (OOP)**.*  
>  
> *First, we implemented the **Generic DAO Pattern** using Java Interfaces and Type Generics. In `CrudDao<T>`, we define an abstract contract for entity persistence—including `getAll()`, `getById()`, `add()`, `update()`, and `delete()`. This interface is concretely implemented by `InventoryDao` and `InvoiceDao`, enforcing high cohesion and decoupling our business logic from raw SQL.*  
>  
> *Second, we built an **Inheritance and Polymorphism hierarchy** for warehouse hardware modeling:*  
> - *`IndustrialDevice` serves as the top-level interface contract.*  
> - *`AbstractWarehouseActuator` provides an abstract class layer implementing shared state like hardware coils, directional logic, and operational health diagnostics.*  
> - *Concrete subclasses like `BeltConveyorDevice` and `OpticalSensorDevice` override specialized behavior.*  
>  
> *Furthermore, all our domain models—`Product`, `Invoice`, `ModbusTag`—strictly adhere to **Encapsulation** with private state, immutable identifiers, and explicit domain validation."*

---

### 📍 [02:30 – 03:30] Point 3: JavaFX UI Design & Layout Controls (60s)
**🖥️ What to show on screen:**
- Open `main-app-view.fxml` in the IDE or Scene Builder, and show the running UI.
- Click through the left sidebar navigation: **Dashboard**, **SCADA Station**, **Storage Matrix**, **Inventory Ledger**, **Finance & Invoicing**, and **Settings / Tags**.
- Point out the `PasswordField` on the Login view, `BorderPane`, `StackPane`, `GridPane`, `TableView`, `ComboBox`, and custom `Canvas`.

**🗣️ Spoken Script:**
> *"For our **JavaFX UI Design**, we designed a clean, modern interface implemented in pure JavaFX without brittle external CSS.*  
>  
> *We utilize a wide variety of JavaFX layout panes according to their specialized strengths:*  
> - *The root layout is a **`BorderPane`**, providing a persistent top telemetry bar and a responsive left navigation rail.*  
> - *The central workspace is hosted inside a **`StackPane`**, enabling instantaneous zero-flicker view swapping across modules.*  
> - *Our **Storage Matrix** and SCADA grid use dynamic **`GridPane`** layouts to render high-bay storage racks and floor equipment.*  
> - *Long data streams use **`ScrollPane`** with `fitToWidth` enabled.*  
>  
> *For UI controls, we showcase **`PasswordField`** for secure credential masking, **`TableView`** with custom cell formatters, **`ComboBox`** for batch dispatch and currency selection, **`Canvas`** for real-time 60-FPS vector graphic animation of cranes and conveyor belts, and circular vector shapes for live Modbus heartbeat indicators."*

---

### 📍 [03:30 – 04:15] Point 4: Layout Responsiveness & Window Binding (45s)
**🖥️ What to show on screen:**
- Grab the edge of the running application window and resize it: shrink it down, stretch it horizontally, maximize it to full screen, and restore it.
- Show how the KPI cards, tables, and toolbars automatically reflow and scale smoothly.
- Briefly highlight in `main-app-view.fxml` the properties: `HBox.hgrow="ALWAYS"`, `VBox.vgrow="ALWAYS"`, and `fitToWidth="true"`.

**🗣️ Spoken Script:**
> *"A key requirement of this project is **Layout Responsiveness**.*  
>  
> *Notice that when I resize the application window—from a compact 1080p window to full ultra-wide screen—the layout never breaks or clips.*  
>  
> *We achieved this by avoiding hardcoded absolute pixel positioning. Instead, we use JavaFX layout constraints:*  
> - *`HBox.hgrow="ALWAYS"` and `VBox.vgrow="ALWAYS"` allow workspace cards and tables to expand proportionally.*  
> - *Our custom SCADA `Canvas` listens dynamically to parent width and height change listeners, recalculating aspect ratios and conveyor coordinates in real time.*  
> - *Tables and grids wrap cleanly inside scroll panes with `fitToWidth` bound to parent bounds."*

---

### 📍 [04:15 – 05:15] Point 5: Concurrency & Multi-Threading (60s)
**🖥️ What to show on screen:**
- Open `WarehouseBuffer.java` (`src/main/java/.../concurrency/WarehouseBuffer.java`).
- Highlight lines with `ReentrantLock(true)`, `notFull.await()`, and `notEmpty.signal()`.
- Open `ConveyorProducerService.java` showing thread pools / background workers.
- Show `Platform.runLater()` calls in `MainAppController.java`.

**🗣️ Spoken Script:**
> *"Now let's examine **Concurrency and Multi-Threading**.*  
>  
> *To simulate high-speed factory infeed and outfeed, we implemented the classical **Producer-Consumer Pattern** in `WarehouseBuffer.java`.*  
>  
> *Rather than naive polling, our buffer uses explicit synchronization primitives from `java.util.concurrent.locks`:*  
> - *A fair **`ReentrantLock`** guarantees FIFO access across threads.*  
> - *Two condition variables—**`notFull`** and **`notEmpty`**—coordinate worker threads. When the conveyor queue is full, the Producer thread safely blocks via `notFull.await()` without burning CPU cycles. When an item is ingested, it signals waiting consumers via `notEmpty.signal()`.*  
>  
> *Furthermore, our Modbus PLC polling and REST API calls run on background daemon threads and thread pools. To maintain strict JavaFX thread safety, all background UI updates are marshaled back to the UI thread using **`Platform.runLater()`**, completely preventing JavaFX UI freezing or concurrency race conditions."*

---

### 📍 [05:15 – 06:15] Point 6: Database Integration & Relational Schema (60s)
**🖥️ What to show on screen:**
- Open `DatabaseManager.java` (`src/main/java/.../database/DatabaseManager.java`).
- Highlight the SQL `CREATE TABLE` statements for `users`, `inventory`, `invoices`, and `invoice_items`.
- Point out the `FOREIGN KEY` constraints and `ON DELETE CASCADE`.

**🗣️ Spoken Script:**
> *"Next is our **SQLite Database Integration**.*  
>  
> *All warehouse state is persisted locally using SQLite via JDBC in `DatabaseManager.java`. Our schema models real-world enterprise relational structures:*  
> - *The **`users`** table stores operator credentials with unique constraints and timestamping.*  
> - *The **`inventory`** table tracks physical SKUs, product categories, quantities, unit prices, and high-bay slot locations.*  
> - *The **`invoices`** table establishes a **One-to-Many Relationship** with `users`, linking purchase orders to the authorized operator with `FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE`.*  
> - *The **`invoice_items`** line-item table establishes a normalized **Many-to-Many bridge** linking invoices to inventory items with foreign keys to both `invoices(id)` and `inventory(id)`.*  
>  
> *All multi-item invoice checkouts utilize database transactions with `setAutoCommit(false)` and atomic `commit()`, preventing partial writes in case of system interruption."*

---

### 📍 [06:15 – 07:00] Point 7: Data Manipulation (Full CRUD Live Demo) (45s)
**🖥️ What to show on screen:**
- Switch to the running Kanshi app and click on **Inventory Ledger**.
- Perform a live CRUD cycle:
  1. **Create:** Click `+ Add Product`, enter SKU `SKU-DEMO-99`, Name `Precision Sensor`, Category `Sensors`, Qty `25`, Price `$45.00`, Bin `B-01-01`. Click Save. Show it in the table.
  2. **Read:** Type `DEMO` in the search bar. Show instant table filtering.
  3. **Update:** Click `Adjust` on `SKU-DEMO-99`, update quantity to `50`. Show updated Total Value.
  4. **Delete:** Click `Delete` on `SKU-DEMO-99`, confirm the prompt. Show the row cleanly removed from both the TableView and SQLite.

**🗣️ Spoken Script:**
> *"Now I will demonstrate full **CRUD Data Manipulation** live in our Inventory Ledger:*  
> - ***Create:** I click '+ Add Product', enter a new SKU, name, quantity of 25, and price of $45. Upon saving, it executes a SQL PreparedStatement INSERT and appears immediately in our ledger.*  
> - ***Read:** Using our live search filter, I type 'DEMO'—the table queries and filters matching records dynamically.*  
> - ***Update:** I click 'Adjust', update the quantity from 25 to 50, and save. The record is updated in SQLite via SQL UPDATE, and the catalog valuation recalculates.*  
> - ***Delete:** Finally, I click 'Delete' and confirm. The record is purged with SQL DELETE, and the UI immediately synchronizes.*  
>  
> *All CRUD operations are fully atomic and synchronized with our dashboard KPI metrics."*

---

### 📍 [07:00 – 07:45] Point 8: Networking & JSON REST API Parsing (45s)
**🖥️ What to show on screen:**
- Switch to the **Dashboard** view.
- Highlight the **Live FX Telemetry Strip** (`🌐 LIVE FX TELEMETRY: 1 USD = €0.88 EUR | £0.75 GBP ...`).
- Click the **`💱 FX:` currency dropdown** and switch from `USD` to `EUR`, then to `JPY`.
  - Point out how **"TOTAL ASSET VALUATION"** instantly converts from `$24,580.00` to `€22,613.60` and `¥3,809,900`.
- Open `CurrencyApiService.java` and `ExchangeRateResponse.java` in the IDE to show `HttpClient` and `Gson.fromJson()`.

**🗣️ Spoken Script:**
> *"Finally, we demonstrate **Networking and JSON Data Parsing**.*  
>  
> *Modern warehouses participate in global supply chains, so Kanshi connects to live international financial markets via REST API:*  
> - *In `CurrencyApiService.java`, we use the modern Java 21 **`HttpClient`** to perform asynchronous non-blocking HTTP GET requests to `api.frankfurter.dev`, which serves public reference exchange rates from the European Central Bank.*  
> - *When the HTTP 200 payload arrives, we deserialize the raw JSON string into strongly-typed Java DTOs using Google's **`Gson`** library (`ExchangeRateResponse.java`).*  
> - *On the dashboard, you can see our live FX ticker. When I change the currency dropdown to **Euro** or **Japanese Yen**, the system dynamically queries the live exchange rate and recalculates our total warehouse inventory asset valuation in real time.*  
> - *If the network drops, built-in fallback baseline rates ensure zero downtime."*

---

### 📍 [07:45 – 08:00] Conclusion & Wrap-Up (15s)
**🖥️ What to show on screen:**
- Return to the Dashboard or SCADA Studio view with active animations.

**🗣️ Spoken Script:**
> *"In summary, Kanshi Warehouse Management System successfully fulfills and exceeds every project objective: continuous Git version control, rigorous OOP architecture, responsive JavaFX interface design, thread-safe Producer-Consumer concurrency, a relational SQLite database with foreign keys, full CRUD capabilities, and live JSON REST API integration.*  
>  
> *Thank you very much for your time. I am now open to any questions!"*

---

## 💡 Quick Tips for Recording Your 8-Minute Video

1. **Keep IntelliJ & App Ready:** Launch Factory I/O (or have it in test mode) and have the Kanshi app running on your secondary monitor before hitting Record.
2. **Pacing:** Stick strictly to the time markers above. If you spend too long on OOP, you may run out of time for REST API and CRUD.
3. **Audio Clarity:** Speak clearly and with energy; examiners appreciate confident explanations of design decisions (e.g. *"we chose `ReentrantLock` over synchronized blocks because..."*).
4. **Mouse Pointer:** Use your cursor deliberately to point at the exact code lines and UI widgets as you mention them.
