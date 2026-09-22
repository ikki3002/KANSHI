<!-- Alias file pointing to checklist_topics.md -->
# Kanshi Warehouse Management & SCADA System
## Academic Syllabus & Lab Topics Verification Checklist

> **Note**: For the full interactive checklist and architecture mapping, see [checklist_topics.md](file:///c:/Users/Lenovo/IdeaProjects/kanshi-warehouse-management-system/checklist_topics.md).

| Week | Topic Area | Subtopics / Concepts | Application in Kanshi WMS/SCADA | Status | Location / Implementation |
| :--- | :--- | :--- | :--- | :---: | :--- |
| **Week 1** | **Java Basics & Core OOP** | Compilation (`javac`/`java`), data types, control flow, methods, classes, encapsulation, inheritance | Model classes (`User`, `Product`, `StockTransaction`), `PasswordValidator` | `[x]` | `model/`, `service/` |
| **Week 2** | **Git & Version Control** | `git init`, staging, commits, branches, `.gitignore`, GitHub repo | Version control history, clean commits per feature | `[ ]` | Git repository |
| **Week 3** | **Desktop GUI with JavaFX** | Stages, Scenes, Panes, Controls, Event handling, CSS styling, Canvas graphics | **Login & Registration Portal**, **Dark Industrial Theme**, **Times New Roman** | `[x]` | `view/`, `controller/` |
| **Week 4** | **Multithreading & Concurrency** | `Thread`, `Runnable`, `Platform.runLater()`, `ScheduledExecutorService`, synchronization | Pure Java Modbus TCP client & FactoryIOService background polling engine | `[x]` | `service/modbus/` |
| **Week 5** | **Lab Milestone / Review** | Weeks 1-4 consolidation and quiz preparation | Code audit and integration checks | `[ ]` | Milestone audit |
| **Week 6** | **Relational DB (SQLite + JavaFX)** | JDBC driver, `Connection`, `PreparedStatement`, CRUD, `TableView` binding | Persistent SQLite database for Users, initial credentials seeding, registration | `[x]` | `database/`, `dao/` |
| **Week 7** | **JSON Parsing & External APIs** | JSON parsing, HTTP client, API response handling | Modbus register map config JSON, dynamic Tag Settings manager | `[x]` | `service/modbus/` |
