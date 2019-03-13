The javadoc-main.css file is built based on the js sdk's Typedoc theme:
https://github.com/mongodb/stitch-js-sdk/tree/master/typedoc-theme

We just want the font-awesome icons and a few colors for the feedback
widget, so it's not worth bringing the theme build boilerplate over
at this time.

To recreate, add a javadoc-main.sass to stitch-js-sdk/typedoc-theme/src/assets/css:
```
@import constants

@import vendors/fontawesome/fontawesome.scss
@import vendors/fontawesome/solid.scss
```
Edit stitch-js-sdk/typedoc-theme/src/assets/css/vendors/fontawesome/_variables.scss to remove 
the relative path ("../") from the fa-font-path variable:
```
$fa-font-path:                "fonts";
```
Then run stitch-js-sdk/typedoc-theme/build.sh and copy the resulting
bin/assets/css/javadoc-main.css to this directory.
