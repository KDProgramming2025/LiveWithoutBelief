- The admin panel looks like a modern professional dashboard. It has a menu on the left that has a list of the items, and clicking on each item does something.
- The admin panel should have a login form to log in and persist logged in on page refresh. 
- The items in the left menu are: Article management, User Management and a Logout button.

    - Menu Items management opens a page on the right side of the menu and the page shows the list of menu items currently in the app. The list should show the title, icon, order number, label, and action buttons: move up, move down, edit title and label, edit icon, delete item. This page should also have a form to add a new menu item with fields for title and select icon from local pc.

    - Article management opens a page on the right side of the menu and the page shows the list of articles currently on the server. The list should show the filename, its path on the server, creation date, its title, its cover image, its icon, its labels, and its order number in the menu that will be shown to the user in the main page of the Android app. The list should also have action buttons: move up, move down, edit title and labels, edit cover image, and edit icon.
    This page should have the feature to upload new docx files and the uploaded file should be added to the end of the list of the articles.

    - User Management opens a page on the right side of the menu and the page shows the number of registered users, a search field to find a user, and a list below it to show the found users.
    The list shows the user's username, registration date, number of their bookmarks, number of their discussion threads, last login date, and an action button to remove the user.

- The docx upload form should have the fields for Title, Select image from local pc for the cover, And Select image from local pc for the icon.
And after uploading the files, the backend should convert the docx file to html,css,js format and put them in the proper directory for serving the app. 