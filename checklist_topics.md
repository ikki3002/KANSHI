# Kanshi Warehouse Management & SCADA System
## Academic Syllabus & Lab Topics Verification Checklist

This document tracks all required lab topics and subtopics from the course syllabus, mapping them directly to practical implementations within the **Kanshi WMS & SCADA** application.

---

## 📋 Course Topics Implementation Matrix

| Week | Topic Area | Subtopics / Concepts | Application in Kanshi WMS/SCADA | Status | Location / Implementation |
| :--- | :--- | :--- | :--- | :---: | :--- |
| **Week 1** | **Java Basics & Core OOP** | - Compilation & Execution (`javac`, `java`, JVM/JRE)<br>- Data types, variables, operators<br>- Control flow (`if/else`, `switch`, loops)<br>- Methods, method overloading<br>- Classes, objects, constructors<br>- Encapsulation, getters/setters, inheritance | - Model classes: `User`, `Product`, `StockTransaction`<br>- Business logic & validation: `PasswordValidator` | `[x]` | `model/User.java`, `service/PasswordValidator.java` |
| **Week 2** | **Git & Version Control** | - Repository initialization (`git init`)<br>- Staging & Committing (`git add`, `git commit`)<br>- Branching strategy (`feature/login`, `feature/scada`)<br>- Meaningful commit messages<br>- Remote repo integration (GitHub) | - Clean commit history per feature<br>- `.gitignore` configured for Java/Maven/IntelliJ<br>- Non-modular Maven project structure | `[ ]` | Project root & Git log |
| **Week 3** | **Desktop GUI with JavaFX** | - JavaFX Application lifecycle (`start()`, `Stage`, `Scene`)<br>- Non-modular launching via `Launcher.java`<br>- Layout panes (`BorderPane`, `StackPane`, `VBox`, `HBox`)<br>- Core controls (`Button`, `TextField`, `PasswordField`, `ProgressBar`, `ComboBox`, `Label`)<br>- Event handling (ActionEvents, ChangeListeners)<br>- Custom JavaFX CSS styling (Industrial dark theme, Times New Roman)<br>- Maximized screen window configuration | - **Login & Registration Portal**: Secure sign-in UI, input validation, role selection, dynamic password strength feedback<br>- **Dark Industrial Theme**: Deep slate/charcoal palette, safety amber accents, Times New Roman global typography<br>- **Home Dashboard**: (Next phase)<br>- **SCADA Panel**: (Next phase) | `[x]` | `login-view.fxml`, `LoginController.java`, `css/industrial-dark.css`, `HelloApplication.java` |
| **Week 4** | **Multithreading & Concurrency** | - `Thread` & `Runnable`<br>- Thread lifecycle & daemon threads<br>- `Platform.runLater()` (JavaFX UI thread dispatching)<br>- `ScheduledExecutorService` / `Task` / `Service`<br>- Synchronization & thread-safe state sharing (`volatile`, `synchronized`, `AtomicInteger`)<br>- Avoiding UI freeze during long operations | - **OT Modbus Polling Engine**: Continuous background daemon reading PLC registers without freezing the UI<br>- Socket client with atomic transaction counter<br>- Real-time Vision Sensor polling & actuator dispatch | `[x]` | `service/modbus/ModbusTcpClient.java`, `service/modbus/FactoryIOService.java` |
| **Week 5** | **Lab Milestone / Review** | - Consolidation of Weeks 1–4<br>- Integrated code review, refactoring, bug fixes | - Midterm project audit<br>- End-to-end IT-to-GUI event propagation test | `[ ]` | Test suite / Milestone tag |
| **Week 6** | **Relational Database (SQLite + JavaFX)** | - SQLite JDBC driver integration<br>- Connection management (`DriverManager`, Connection singleton/factory)<br>- Relational schema creation (`CREATE TABLE`, foreign keys, indexes)<br>- Parameterized `PreparedStatement` (SQL injection prevention)<br>- CRUD operations: `INSERT`, `SELECT`, `UPDATE`, `DELETE`<br>- Data binding with JavaFX `TableView` (`ObservableList`) | - Persistent storage for Users & Roles (`ADMIN`, `OPERATOR`, `SUPERVISOR`)<br>- Automatic SQLite database creation & credential seeding<br>- Account registration and credential verification | `[x]` | `database/DatabaseManager.java`, `database/UserDao.java`, `kanshi.db` |
| **Week 7** | **JSON Parsing & External APIs** | - JSON data structures (objects, arrays, primitives)<br>- Parsing JSON responses into Java model objects<br>- HTTP URL connection handling (`HttpClient` / `HttpURLConnection`)<br>- Exporting/importing warehouse inventory as JSON<br>- Parsing external API data | - Configuration loader for Modbus PLC register maps via `warehouse_tags.json`<br>- Dynamic tag loader & visual Tag Settings manager | `[x]` | `model/ModbusTag.java`, `service/modbus/TagManager.java`, `warehouse_tags.json` |

---

## 🛠️ Architecture & Principles

1. **Non-Modular JavaFX Architecture**:
   - Uses `Launcher.java` to invoke `HelloApplication.class` on standard classpath.
   - Eliminates `module-info.java` reflection/export conflicts with SQLite JDBC, Modbus libraries, and JSON parsers.
2. **"Fat-Free, Not Malnourished" Code Standard**:
   - **Fat-free**: No unnecessary design pattern overhead, enterprise bloat, or complex DI frameworks.
   - **Nutritious**: Clean separation of concerns (Model-View-Controller/Service-DAO), descriptive variable names, comprehensive error handling.
3. **Pure JavaFX Graphics**:
   - No heavy 3rd-party game or 3D engines.
   - Custom SCADA instrumentation built using JavaFX `Canvas`, layout controls, SVG paths, and CSS styling.
4. **IT & OT Convergence**:
   - **IT Layer**: Authentication, Inventory Ledger, Pricing, Order Booking, SQLite CRUD.
   - **OT Layer**: Modbus TCP communication, PLC tag polling, Sensor feedback, Actuator dispatch (Conveyor start/stop, Diverter arm, E-Stop).

---

## 📌 Progress Tracker

- [x] **Step 1**: Foundation setup (Non-modular Maven config, SQLite JDBC, JUnit 5, Gson).
- [x] **Step 2**: Modern, clean Login & Registration Portal with live password strength checker and Times New Roman dark industrial theme.
- [ ] **Step 3**: Main Dashboard (Home View) with IT overview cards and OT status strip.
- [x] **Step 4**: SQLite Database initial schema and Data Access Objects (`users` table, seed admin, registration).
- [x] **Step 5**: Background Modbus Worker & Concurrency Engine (Thread / ScheduledExecutorService).
- [ ] **Step 6**: Live SCADA Mimic Display (JavaFX Canvas / Shapes).
- [x] **Step 7**: JSON Config & Tag Settings Management (`warehouse_tags.json`, `TagManager`).
