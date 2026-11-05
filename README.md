# Advanced Attendance Management System

An advanced attendance management application originally built by **Sheli**, uploaded and maintained in this repository with her permission.

## About the Project
This project is a Java-based attendance management system designed to efficiently track and manage student attendance. It follows a structured architecture (MVC/DAO pattern) to separate the user interface, business logic, and database operations, ensuring clean and maintainable code.

## Key Features
- Student Management: Add, update, and manage student records.
- Subject Management: Handle different subjects and classes.
- Attendance Tracking: Record and monitor daily attendance.
- User-Friendly Interface: A clean UI styled with custom CSS.
- Data Access Object (DAO) Pattern: Secure and organized database interactions.

## Tech Stack and Architecture
- Language: Java
- UI/Frontend: Java (UI) + CSS (style.css)
- Architecture: MVC (Model-View-Controller) and DAO (Data Access Object) pattern

### Project Structure
Here is how the core components are organized:

```text
AdvancedAttendanceManagementSystem/
└── src/
    └── main/
        ├── java/com/attendance/app/
        │   ├── Student.java            (Model)
        │   ├── Subject.java            (Model)
        │   ├── StudentDao.java         (Data Access Object)
        │   ├── StudentController.java  (Controller)
        │   └── Ui.java                 (User Interface)
        └── resources/styles/
            └── style.css               (UI Styling)# sheli-attendance-app
Attendance application project by Sheli, uploaded and maintained here with permission. Tracks attendance records with a simple, user-friendly interface.
