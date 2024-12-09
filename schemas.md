Each schema will be converted into system/user messages array.
It is split by sections starting with:

`# {type}`

Types:

- System: A system message
- User: A user message
- Comment: A comment like this one, it is ignored
- System-loop: Used for files, converted into an array of system messages (one per file)

Also you must provide a Settings section

* Run for each file: True/False // should run AI for each input file or just once
* output extension: txt // what extension to give to files in case couldn't terminate an extension
* input extension:  txt // when processing multiple files it filters the input by file extension and work on all the project
* multi files output: True/False // will the output of a single AI call be one file or many
* override files: True/False // it will only attempt to update existing files and will not create new ones.

Run time params:

- {package}: The package/namespace for the output code
- {context}: The files context (used in System-loop)
- {main_file}: The main bullet file to be rebulified
- {prompt}: User prompt for rebulification
- {project_details}: General Project details