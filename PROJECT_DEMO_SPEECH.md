# 7.5-Minute Project Demonstration Speech (Easy English)
**Project Title:** KANSHI — Industrial Warehouse SCADA & Management System  
**Estimated Spoken Duration:** ~7.5 Minutes (At 1.5x video playback speed, this fits within exactly 5:00 minutes).

---

## Quick Recording Tips Before You Start
1. **Pacing:** Speak clearly, warmly, and naturally. You don't need complicated words—plain, confident English is best.
2. **On-Screen Cues:** Follow the `[ON SCREEN ACTION]` markers so the examiner sees the code and UI match what you are talking about.
3. **Speeding Up:** If you speak at a relaxed pace for 7.5 minutes, you can speed up the final video to **1.5x** speed in any free video editor (CapCut, Premiere, Clipchamp) to hit the **5-minute video limit** seamlessly.

---

### [0:00 - 0:45] 1. Introduction & Project Overview
**[ON SCREEN ACTION]:** Start on the Application Login screen (`login-view.fxml`). Log in with username `admin` and password to land on the Main SCADA Dashboard.

> "Hello everyone, and welcome to the project demonstration of our system: **KANSHI — Warehouse Management and Industrial SCADA System**.
>
> In modern supply chains, factories and warehouses need reliable, real-time control to store, move, and track inventory safely. We designed KANSHI to combine high-level warehouse business logic with industrial floor automation.
>
> In this presentation, I will walk you through our complete software architecture, showing how our project satisfies every single lab requirement from Lab 1 to the final week. Let's begin!"

---

### [0:45 - 1:30] 2. Version Control & Git History
**[ON SCREEN ACTION]:** Switch to browser showing GitHub repository page. Click on the **Commits** tab, scroll through commits starting from September 6th all the way to September 28th.

> "First, let's look at our **Version Control** on GitHub.
>
> We started our Git repository right from the idea submission date on **September 6th**. As you can see from our commit log, we practiced regular, incremental development throughout the entire month. 
>
> We used meaningful commit messages, feature branches, and continuous progress tracking. Every major milestone—from our core OOP data structures, database setup, multithreading pipeline, all the way to our live SCADA grid designer and Telegram bot—was safely tracked and preserved through Git. No giant last-minute dumps; our history reflects true engineering practice."

---

### [1:30 - 2:20] 3. Advanced OOP Concepts
**[ON SCREEN ACTION]:** Switch to IntelliJ IDEA. Open `model/` package. Show `ModbusTag.java`, `Product.java`, and interfaces like `BufferChangeListener` or service classes.

> "Next, let's discuss **Advanced Object-Oriented Programming**.
>
> Throughout the project, we applied OOP principles to keep our code clean, scalable, and maintainable:
>
> 1. **Encapsulation:** In classes like `Product` and `ModbusTag`, all fields are private with strict getter and setter validation, ensuring data integrity.
> 2. **Abstraction & Interfaces:** We designed decoupled contracts such as functional listeners—for example, `BufferChangeListener` in our concurrency pipeline and state change listeners on our hardware tags.
> 3. **Enums & Polymorphism:** We used strongly-typed enums like `TagType`—which separates `COIL`, `DISCRETE_INPUT`, and `HOLDING_REGISTER`—and polymorphism across our operational services and database mappers.
>
> This clean OOP structure allows new sensors, actuators, or warehouse hardware to plug in easily without breaking existing code."

---

### [2:20 - 3:15] 4. JavaFX UI Design & Layout Controls
**[ON SCREEN ACTION]:** Switch to the live running application. Click between the sidebar modules: **SCADA Mimic**, **Inventory Ledger**, **Floor Designer**, **Financial Invoicing**, and **Telegram Bot**.

> "Now, let's look at the **JavaFX User Interface Design**.
>
> We built an enterprise-grade dark SCADA interface using FXML and modern styling:
> - **Wide Variety of Panes:** We used **BorderPane** for the overall application frame, **StackPane** for layer-switching between views, **GridPane** for aligned form inputs and metrics, **VBox** and **HBox** for flexible toolbar alignments, and **ScrollPane** to support large data feeds.
> - **Diverse UI Controls:** Our application includes `PasswordField` for secure authentication, `TableView` with dynamic sortable columns for inventory and transactions, interactive `Canvas` nodes for 2D warehouse floor rendering, custom styled buttons, status badges, and combo boxes.
>
> We also replaced broken Unicode emojis with crisp, professional SCADA typography like OP, WSS, and PLC badges, guaranteeing perfect rendering on any operating system."

---

### [3:15 - 3:55] 5. UI Layout Responsiveness
**[ON SCREEN ACTION]:** Click the window maximize/restore button, and grab the window edges to resize the window dynamically. Click the Sidebar Toggle button (`[<]` / `[>]`) to collapse and expand the sidebar.

> "Another key requirement is **Layout Responsiveness**.
>
> A real industrial monitoring app must adapt to different screen resolutions, from control-room monitors to laptops:
> - Notice how our layout dynamically adjusts when I maximize the window or resize its width and height.
> - We achieved this by using JavaFX property bindings, layout priority constraints like `HBox.hgrow='ALWAYS'` and `VBox.vgrow='ALWAYS'`, and `AnchorPane` boundary constraints.
> - We also implemented a collapsible sidebar rail. With one click, the navigation bar collapses into a compact icon rail, automatically giving more screen room to the live SCADA mimic and Floor Designer canvas."

---

### [3:55 - 4:55] 6. Concurrency & Multi-Threading
**[ON SCREEN ACTION]:** In IntelliJ IDEA, open `WarehouseBuffer.java`, `ConveyorProducerService.java`, and `IntakeConsumerService.java`. Then switch to the app's SCADA view and click **Start Pipeline** or infeed simulation to show items moving.

> "Now, let's examine **Concurrency and Multi-Threading**.
>
> In a high-speed automated warehouse, conveyor belts and crane lifters operate at the same time without blocking the UI thread.
> - To solve this, we implemented the classical **Producer-Consumer pattern** using a thread-safe bounded buffer in `WarehouseBuffer.java`.
> - Instead of naive synchronization, we used Java's advanced `ReentrantLock` along with two explicit `Condition` variables: `notFull` and `notEmpty`.
> - When the infeed buffer is full, the producer thread safely awaits space. When items arrive, it signals the consumer crane worker.
> - We managed background operations using an `ExecutorService` thread pool. Any status updates are safely passed back to the JavaFX Application Thread using `Platform.runLater()`, preventing any UI freezing."

---

### [4:55 - 5:45] 7. SQLite Database Integration & Table Relationships
**[ON SCREEN ACTION]:** Open `DatabaseManager.java` in code, or open an SQLite table viewer showing the tables: `users`, `inventory`, `transactions`, `invoices`, `hardware_tags`, and `app_settings`.

> "Next is our **SQLite Database Integration**.
>
> All warehouse data is permanently saved in a relational SQLite database called `warehouse_scada.db`:
> - We established clear relational schemas:
>   - The `users` table manages authenticated warehouse operators with hashed credentials.
>   - The `inventory` table tracks stored pallets, bay locations, quantities, and pricing.
>   - The `transactions` table records every operational audit—such as putaways, dispatches, and crane actions—with timestamps.
>   - The `invoices` table manages financial customer billing records linked to warehouse dispatches.
>   - We also have dedicated tables for `hardware_tags` and `app_settings` so that tag setpoints and Telegram credentials are never forgotten when the app restarts."

---

### [5:45 - 6:35] 8. Data Manipulation: Full CRUD Demonstration
**[ON SCREEN ACTION]:** In the live app, go to **Inventory Management** table:
1. **Create:** Click *Add Product*, fill in SKU `SKU-TEST-99`, Name `Smart Sensor`, Qty `50`, Price `120.00`, Location `Bay-07`. Click *Save*. (Show it appear in table).
2. **Read:** Type `SKU-TEST` in the Search bar. (Show table filter immediately).
3. **Update:** Select the product, click *Edit*, change Quantity to `75`. Click *Update*. (Show updated value).
4. **Delete:** Select the item and click *Delete*. Confirm the deletion dialog. (Show item removed).

> "Now, let's demonstrate a complete **CRUD cycle** on live data.
>
> Watch our Inventory Ledger:
> - **Create:** I will add a new product: SKU `SKU-TEST-99`, named `Smart Sensor`, quantity `50`, located at `Bay-07`. When I click save, `InventoryDao.addProduct()` writes it to SQLite, and it instantly appears in our TableView.
> - **Read:** When I type into our search bar, our filtered observable list immediately performs a live search across all rows.
> - **Update:** Now I will select the product, click Edit, and update its quantity from 50 to 75. It persists straight into the database.
> - **Delete:** Finally, I click Delete and confirm. The record is permanently deleted from SQLite and removed from the screen.
>
> That completes the full Create, Read, Update, and Delete lifecycle."

---

### [6:35 - 7:15] 9. Networking & JSON Parsing
**[ON SCREEN ACTION]:** 
1. In code, show `CurrencyApiService.java` (pointing out `HttpClient` and `Gson.fromJson`).
2. In app, switch to **Financial Invoicing** view, click *Convert Currency* (e.g. USD to EUR / GBP) showing live rates.
3. Switch to the **Telegram Bot** tab, click *Send Test Ping*, and show the live log box updating.

> "Finally, let's explore **Networking and Data Parsing**.
>
> In `CurrencyApiService.java`, our application connects over the internet using Java's modern `HttpClient` to make asynchronous HTTP GET requests to a live currency REST API.
> - We receive the live response payload in JSON format.
> - Then, using Google's **Gson** library, we parse the raw JSON string directly into strongly typed Java objects.
> - This allows our warehouse invoicing module to convert product valuations into multiple global currencies like Euros and British Pounds with live exchange rates!
> - Furthermore, our **Telegram Bot Service** communicates over HTTPS with the Telegram Bot API, sending operational alerts and receiving remote inventory query commands in real time."

---

### [7:15 - 7:35] 10. Conclusion & Wrap Up
**[ON SCREEN ACTION]:** Return to the main SCADA dashboard showing the live mimic, system clocks, and status indicators.

> "To conclude, **KANSHI** brings together every single concept from our course: from solid OOP foundations and Git version control, to an advanced responsive JavaFX interface, multi-threaded bounded buffers, SQLite relational database persistence, full CRUD operations, and live internet networking with JSON parsing.
>
> Thank you very much for your time and evaluation!"

---

## Recording Checklist for the 5-Minute Video Limit
- [ ] **Recording:** Record your screen and microphone while reading the script above. (Total unhurried recording time: ~7 to 7.5 minutes).
- [ ] **Editing / Playback Speed:** Import the video into Clipchamp, CapCut, or DaVinci Resolve. Increase playback speed to **1.4x or 1.5x**.
- [ ] **Verify Duration:** Confirm the final exported MP4 is **under 5 minutes** (e.g., 4 minutes 45 seconds).
- [ ] **Submission Check:**
  - Submit the Google Form before September 28th midnight.
  - Submit your GitHub repository link.
  - Make sure the project title matches exactly: **KANSHI — Warehouse SCADA & Automated Management System**.
