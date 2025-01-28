# Bulifier Release Notes

## Release 2.2.19
 - Updated items order in jobs scheduler, added `created` field and using that instead the `last_update`
 - Added a revert view screen for git. You can revert to any commit from ui list
 - Added autocommit option for agent, commit before agent is running
 - Changed all the git notifications
    - added the branch to top toolbar 
    - added color indication for progress, success and error
    - Removed the option to manually creating folders and files
 - Added a new git_roots.settings file to manage sync with git. Only folders in the list are deleted before exporting db files to local git repo. This solve all the issues with deleting files
    - Added a shortcut to add folders to git_roots from folders menu. It will auto figure if child folders needs to be removed or parent folder already added and this request is redundant



## Release 2.1.05
 - Updated schemas
   - updated bulify schema 
     - added examples
     - using the name from the ai output instead of relying on settings
     - upending .bul to what ever ai gives back like game.py.bul
   - updated debulify schema
     - now it can generate output with multiple file types
       - no longer relying solely on output file extension from settings
       - if the file is with the structure of "xxxx.xxx.bul" it will remove the bul and use the remaining extension
  - Improved ui
   - Removed bottom bar
   - Moved git to top bar
   - Added clear action button to invoke AI
 - Update GIT
   - clone as is - made it easier to work with existing projects by cloning them as is
   - Adjusted the project sync functionality - Instead of deleting all the local files 
     it will delete only the files in folders used by the project

## Release 2.0.17
 - switched to costume navigation system to better support cross module navigation
 - Added hilt
 - Temporary disabled the retry and reapply functionality since it is not fully implemented
   - To retry a query just start a new one
 - Fixed a lot of bugs!

## Release 1.1

**New Features:**
- Introduced the ability to rebulify a single file.
- Added this release notes page. 😉
- Enabled full-path folder creation, eliminating the need to create packages one by one.
- Simplified AI interaction to a single button for schema prediction, with manual adjustment options.
- Added path information to the Job Scheduling screen.
- Enabled the option to secure specific model fields as passwords for easier screen sharing.

**Bug Fixes:**
- Resolved an issue where jobs from other projects appeared on the jobs screen.

## Release 1.0

Hello Bulifier - Project launched!
