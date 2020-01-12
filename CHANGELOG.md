# Changelog

##### Version 1.7(2019-10-12)

-   Fixed a few minor issues with building from pipeline [PR #3](https://github.com/jenkinsci/debian-pbuilder-plugin/pull/3)

##### Version 1.6(2019-04-21)

-   Removed file lock when building packages; pbuilder should take care
    of this automatically and it probably doesn't work with multiple
    slaves anyway
-   Catch correct exception if trying to create multiple chroots at the
    same time
-   Added an option to change the pbuilder dependency resolver. 
    Changing the dependency resolver fixes problems with apt segfaulting
    on an armhf chroot.
-   Made the global configuration actually save and load correctly.
-   Print out a more useful error message when the debian/source/format
    file doesn't exist or is bad.

##### Version 1.5(2019-01-27)

-   Added ability to use pristine-tar to get orig.tar.gz
    file(JENKINS-52645)
-   Added ability to set components when creating pbuilder base.  This
    is mostly useful for building Ubuntu packages.

##### Version 1.4(2018-05-12)

-   Added a setting for the keyring.  

    -   Auto-detection for building Debian packages on Ubuntu should
        still work, but this is useful for overriding it

    -   Thanks to jayjlawrence with [PR #1](https://github.com/jenkinsci/debian-pbuilder-plugin/pull/1)
        for prompting this change

##### Version 1.3(2018-01-13)

-   Plugin does not depend on pipeline
-   Fixed JENKINS-48921

##### Version 1.2(2017-12-23)

-   First official release on Jenkins.io
-   No changes to code since version 1.1, only documentation/POM updates

##### Version 1.1

-   Support for Pipeline jobs. 
-   Added configuration options for versioning

##### Version 1.0

-   Initial Release of software.  Can build projects of multiple types.
