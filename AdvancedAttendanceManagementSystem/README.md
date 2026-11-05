# AdvancedAttendanceManagementSystem

Desktop attendance manager built with JavaFX 21, Maven and SQLite (Windows, IntelliJ IDEA).

## Open and run
1. Unzip, then in IntelliJ choose **File > Open** and select the `AdvancedAttendanceManagementSystem` folder (the one with `pom.xml`).
2. Wait for Maven to download libraries (or click the reload icon in the Maven panel).
3. **File > Project Structure > Project**: set the SDK to Java 17 or newer.
4. Open `src/main/java/com/attendance/app/Launcher.java`, right-click and choose **Run 'Launcher.main()'**.

## First use
1. **Student Directory** > Add Student (register each student once).
2. **Semesters & Subjects** > Add Semester and Add Subject (you can also edit or delete them there).
3. Choose semester, subject and date. Students load automatically. Mark P / A, then **Save Attendance**.

Data is stored in `attendance.db` (created in the project folder on first run) and survives restarts.

## Files (package `com.attendance.app`)
- `Launcher`, `Main` - start-up, window and sidebar navigation
- `Database` - SQLite connection and safe table creation
- `Student`, `Semester`, `Subject`, `AttendanceRecord` - data classes
- `StudentDao`, `AttendanceDao` - all SQL (prepared statements, transactions)
- `DashboardController`, `StudentController`, `AttendanceController`, `MarkAttendanceDialog` - the pages
- `ReportController` - CSV export
- `Ui` - shared dialogs and card helper
- `resources/styles/style.css` - all styling

## Editing everything
- **Student Directory**: add, edit, delete students.
- **Semesters & Subjects**: add, rename, delete.
- **Mark Attendance**: P/A and remarks for the whole class; saving again for the same date corrects it.
- **Attendance History > View Details**: select one row, then Edit Selected Record (status + remarks) or Delete Selected Record.
- `ManageController` - the Semesters & Subjects page.
