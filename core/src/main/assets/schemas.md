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
* file extension: txt // what extension to give to files
* multi files output: True/False // will the output of a single AI call be one file or many


Run time params:
- {package}: The package/namespace for the output code
- {context}: The files context (used in System-loop)
- {bullet_file}: The main bullet file to be rebulified
- {prompt}: User prompt for rebulification