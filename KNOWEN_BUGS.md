# Bulifier Known Bugs

- **Project Creation Screen**:
   - The title bar incorrectly allows you to create folders and files, you should not see those buttons there

- **Back Button Behavior**:
   - Clicking "Back" on the first screen does nothing.
      - *Expected*: The app should exit.

- **Path Display Issue**:
   - When starting a new project and creating a path like `"com/game/snake"`:
      - *Expected*: The path should display as `"com/game/snake"` with no folders on the screen.
      - *Actual*: The correct path is shown, but the `"com"` folder appears on the screen.

- **Jobs Scheduler Screen**:
   - Clicking the "New" button on the Jobs Scheduler screen:
      - *Expected*: Opens the job detail screen.
      - *Actual*: Stays on the same screen and only adds the new job to the list.
      - *Note*: I might have fixed it, but it is delicate. This mechanism needs to be better synchronized  

- **Database Indexing**:
   - `project_id` is not indexed properly in all tables; it should be referenced as a foreign key and indexed.

- **Orientation Change**:
   - When changing orientation while viewing a file, the file content disappears.
