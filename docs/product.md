# FlowTask — MVP Brief

## Problem
What frustration does this app solve, and for whom?
Answer: Individual need a better way to create, delete, manage all todo tasks in a single place, priortise accordingly , to not to miss any deadlines.

## Target user
Describe one specific initial user. Do not say “everyone.”
A busy professional that need to manage various tasks for day to day activities.

## Core user journey
As a <user>, I want to <action>, so that <outcome>.
As a professional  individual , I want to create a task so that I can view it later, mark it done. Change priority as well.
As a professional individual, I want to view all my pending task, sorted by priority.
As a professional individual, I want edit my existing task, change priority, set a deadline.
As a busy indivual, I want to reopen existing done task if needed.



## MVP features
Create todo task,
Edit task
Change priority
Set deadline,
Filter by priority,
Sort by priority
Default list view to open task based on priority
Reopen done task

## Non-goals
List features deliberately excluded from the MVP.
User auth,
Project, tag, team assignement.
Assign task to people, team
Notification, file upload
Collaboration, real time upate,
Cloud deployment

## Task rules
Define the rules for a task:
- Which fields does it have?
  Id(auto gen), titile, description(optional), deadline(optional), status(open, completed, due)- dfault open,priority(high, medium, low)- default medium.
- Which fields are required?
  Id, title,status, priority
- What does “complete” mean?
  Satus completed means, this todo has been actioned on by the user and marked completed.
- Can a completed task be edited?
  No, if user want to edit, they need to reopen it and edit.
- What happens when a task is deleted?
  Only user with appropriate role can delete the task, once deleted its gone.

## Success criteria
How will we know the MVP works technically and for its user?
User complete full task lifescyle using browser
User can see and edit task in browser, mobile ui.
Backend api has end to end testing in place for each feature.
Front end able to show meaningfull eror
A new developer can setup the project locally by reading readme.


## Questions / assumptions
List anything you are unsure about.
Do we allow task deadline to be set in past?
When task completed and reopened, do we allow to change due date in past? Or change the due date? How to best way handle it.
What should be default view order to the list.

## Future vision
In future we want to add below:
. project  based todo, each project can have various stage and each stage can have its own todos.
. team level assignement for each project/stage/individul todo
. a sperate action section on todo, where user able to update the progress comment, upload file
. notifiaciton, reminder by whatsapp/emal. 
