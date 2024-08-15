# Bulifier Smells

 - On the project creation screen the title bar allows you to create folders and files
 - Clicking back does nothing when on the first screen.
   - expected: Exit the app
 - start a new project and create a path: "com/game/snake"
   - expected: it will show the path as "com/game/snake" and no folders on the screen
   - actual: it shows the correct path but with the "com" folder on the screen
 - Clicking the "new" button on the Jobs Scheduler screen
   - expected: open the job detail screen
   - actual: it stays on the same screen and only add the new job to the list
 - project_id is not indexes properly on all the table, it needs to be references as foreign key and indexes