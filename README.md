# Debian Pbuilder

This plugin allows you to build deb packages in a pbuilder environment.

This plugin is based largely off of [jenkins-debian-glue.](https://jenkins-debian-glue.org/)

Find pre-built versions of this plugin [on this Jenkins instance!](http://jenkins.rm5248.com/job/debian-pbuilder/)


## Installation

As of right now, this is not hosted on jenkins-ci.org because it is still in its early stages.  If there is some interest in either using this plugin and/or another maintainer to help with it, I am willing to move it to jenkins-ci.org and have it be installed automatically.

## Jenkins Configuration(Traditional project)

1. Create a new project.
1. Set the SVN/git repo to use.  Like jenkins-debian-glue, if you are using SVN, set the "Local module directory(optional)" to "source"; if you are using git set "Additional behaviors/Local subdirectory for repo" to "source".
1. Under 'Build Environment', select 'Delete workspace before build starts'
2. Under the 'Build' section, add 'Debian Pbuilder'
3. If you want to use multiple cores/jobs, set the one configuration variable in this section
4. If you have custom pbuilder hook files that you want to install, install the [Config File Provider Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Config+File+Provider+Plugin) to add in config files.  Set the 'target' option to be `hookdir/<file-name>`
5. If you set the project up as a multi-configuration project, you can set a user-defined axis as "architecture", with the specific architectures that you want to build for(this is the same as jenkins-debian-glue).

## Jenkins Configuration(Pipeline)

1. Create a new Pipeline project
2. Add a node like the following:
```
node(){
        ws{
            stage( "clean" ){
                cleanWs()
            }
            stage("build"){
                //Add whatever parameters you need to the class
                debianPbuilder()
            }
        }
}
```

### Global Configuration

The one global configuration option is to set the e-mail address that will be set in the changelog when the plugin updates the changelog.

## Environment Variables

|Environment Variable|Usage|
|--------------------|-----|
|`DEB_PBUILDER_BUILDING_TAG`|Set this environment variable if you are building a tag.|

## System configuration

On any system that is running the build, serveral support programs must be installed and configured properly.  

1. Install needed denpendencies: `apt-get install qemu-user-static devscripts cowbuilder dpkg-dev`
1. If building Debian packages on Ubuntu, make sure that the package `debian-archive-keyring` is installed
2. Like jenkins-debian-glue, make sure that sudo is configured properly.  As taken from the jenkins-debian-glue webpage, add the following to either /etc/sudoers, or create a new file(e.g. /etc/sudoers.d/jenkins):

  ```
  jenkins ALL=NOPASSWD: /usr/sbin/cowbuilder, /usr/sbin/chroot
  Defaults env_keep+="DEB_* DIST ARCH"
  ```
(this assumes that Jenkins is running under the Jenkins user)

## Output

The output of all commands(pbuilder, etc) can be found in the console of the build.

Once the project has been built, the output files will be automatically added as artifacts.

The format of the files is the same as a normal deb file.  `package-name_version~<date>.[svn|git]<rev>.<build_number>`.  The reason for using the `~` in the name is that that denotes a pre-release version of software.  If the distribution that has been set is not `unreleased`, then the version will have `+0` in place of `~`.

## Other Notes

This plugin assumes that your project is in a `3.0 (native)` format, otherwise dpkg-source will fail.

## License

MIT
