# Debian Pbuilder

This plugin allows you to build deb packages in a pbuilder environment.

This plugin is based largely off of [jenkins-debian-glue.](
https://jenkins-debian-glue.org/)

Find pre-built versions of this plugin [on this Jenkins instance!](
https://jenkins.rm5248.com/job/debian-pbuilder/)

[![Build Status](
https://jenkins.rm5248.com/buildStatus/icon?job=debian-pbuilder)](
https://jenkins.rm5248.com/job/debian-pbuilder/)

## System Setup

Before you can successfully run the plugin, there are certain
requirements that must be met on the node(s) that you wish to run on.  

1.  Install needed dependencies:

    ``` syntaxhighlighter-pre
    apt-get install qemu-user-static devscripts cowbuilder dpkg-dev
    ```

2.  If building Debian packages on Ubuntu, make sure that the package
    debian-archive-keyring is installed 

3.  Like jenkins-debian-glue, make sure that sudo is configured
    properly. As taken from the jenkins-debian-glue webpage, add the
    following to either /etc/sudoers, or create a new file(e.g.
    /etc/sudoers.d/jenkins): 

    ```shell
    jenkins ALL=NOPASSWD: /usr/sbin/cowbuilder, /usr/sbin/chroot, /usr/sbin/pbuilder
    Defaults env_keep+="DEB_* DIST ARCH"
    ```

    (this assumes that Jenkins is running under the jenkins user)

## Using the Plugin

### Configuration Options

There are several global configuration options.  These options may be
found by going to "Manage Jenkins" → "Configure System".  

-   Email address - this is the email that is set in the changelog entry
    for the build.  It need not be an actual email address
-   Version format - Determines how the package will be versioned if not
    a tag build
-   Debian directory - determines where the debian/ folder is, by
    default the project should be checked out to a directory called
    'source'

### Project Setup(Traditional)

This plugin may be configured as both a traditional Jenkins build, or as
a Pipeline project.

1.  Create a new project.  If you want to build for multiple
    architectures, select "Multi-configuration project"
2.  Checkout source code.  When you checkout the source code for the
    project, it **should** be in a subdirectory called 'source'(this
    value can be changed on either a per-build configuration or
    globally).  This can be done as either an SVN repository, or a git
    repository.  To checkout to the proper directory using git, go to
    "Additional Behaviors" and select "Check out to a sub-directory",
    and put "source" as the value.  To checkout to the proper directory
    using SVN, under "Local module directory" put "source" as the
    value.  
    ![](/docs/images/git-settings.png)
    ![](/docs/images/svn-settings.png)

3.  Under 'Build Environment', select 'Delete workspace before build
    starts'

4.  If you have a matrix configuration project, add a new variable
    called "architecture".  Put in the proper architectures to build for
    in this section; this must map to a valid architecture that exists
    in the distribution repos.

    ![](/docs/images/configuration-matrix.png)


5.  Under the 'Build' section, add 'Debian Pbuilder'. Most of these
    settings may be left at their default values, however it is highly
    recommended to fill in the "Distribution" and "Mirror Site"
    variables in order to ensure that you get a consistent build. 
    Otherwise, pbuilder will use the defaults for whatever distribution
    you are currently running.  
    ![](/docs/images/build-configuration.png)

6.  If you have custom pbuilder hook files that you want to install,
    install the [Config File Provider](https://plugins.jenkins.io/config-file-provider)
    to add in config files. Set the 'target' option to be
    \`hookdir/\<file-name\>\`.

### Project Setup(Pipeline)

If using pipeline, you may setup a job similar to the following:

**Pipeline Setup - single arch**

```groovy
node {
    ws {
        stage( "clean" ){
            cleanWs()
        }
        stage( "checkout" ){
            checkout([$class: 'GitSCM', branches: 
                        [[name: '*/jenkinsfile-updates']], 
                        doGenerateSubmoduleConfigurations: false, 
                        extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'source']], 
                        submoduleCfg: [], 
                        userRemoteConfigs: [[credentialsId: '78de5c66-5dfb-4c95-8ad9-ec34e8dee4ec', url: 'git@github.com:rm5248/CSerial.git']]])
        }

        stage("build"){
            //Actually build the package.
            //Note that you can go to the 'Pipeline Syntax' page on your Jenkins instance to generate this automatically
            debianPbuilder additionalBuildResults: '', architecture: '', distribution: 'jessie', keyring: '', mirrorSite: 'http://ftp.us.debian.org'
        }
    }

}
```

The following example shows how to build for multiple architectures:

**Pipeline Setup - Multiple arch**

```groovy
def axisArchitecture = ["amd64", "armhf"]
def axisNode = ["master"]
def tasks = [:]

for( int i = 0; i < axisArchitecture.size(); i++ ){
    def arch = axisArchitecture[i];
    tasks["${axisNode[0]}/${axisArchitecture[i]}"] = {
        node(axisNode[0]){
            ws{
                stage( "clean" ){
                    cleanWs()
                }
                stage( "checkout" ){
                    checkout([$class: 'GitSCM', branches: 
                             [[name: '*/jenkinsfile-updates']], 
                             doGenerateSubmoduleConfigurations: false, 
                             extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'source']], 
                             submoduleCfg: [], 
                             userRemoteConfigs: [[credentialsId: '78de5c66-5dfb-4c95-8ad9-ec34e8dee4ec', url: 'git@github.com:rm5248/CSerial.git']]])
                } 
                stage("build-${arch}"){
                    debianPbuilder architecture:"${arch}"
                }
            }
        }
    }
}

stage('build'){
    parallel tasks
}
```

** Pipeline setup - declarative **

```groovy
pipeline{
    
    agent any
    
    stages{
        stage("clean"){
            steps{
                cleanWs()
            }   
        }
        
        stage("checkout"){
            steps{
                dir('source'){
                    git 'https://github.com/rm5248/CSerial.git'
                }
            }
        }
        
        stage('build'){
            steps{
                debianPbuilder additionalBuildResults: '', 
                    architecture: 'armhf', 
                    artifactoryRepoName: '', 
                    components: '', 
                    distribution: 'buster', 
                    extraPackages: '', 
                    keyring: '', 
                    mirrorSite: 'https://deb.debian.org/debian', 
                    otherMirror: '',
                    pbuilderType: 'PBuilder', pristineTarName: ''
            }
        }
    }
}
```


## Package Versioning

By default the plugin will increment the current version number by
reading 'debian/changelog'.  The algorithm follows the standard Debian
practice of packages having a \~ being a pre-release version, and
packages with a + denoting a version greater than what is default.  Note
that this only happens if the the distribution set in 'debian/changelog'
is UNRELEASED, or a tag is being built.  Tags are automatically scanned;
if using SVN or git, the environment variables SVN\_URL\_1 and
GIT\_BRANCH are scanned for the substring "tags/"; if the substring is
found, the package will be built as a tag.  Otherwise, you can also
select the "Build as tag" to force the package to not increment the
version number, or alternatively set the environment
variable DEB\_PBUILDER\_BUILDING\_TAG if you are building a tag.

## Artifacts

All generated files are automatically added to the build artifacts for
easy retrieval.  This includes the deb files, as well as the dsc and tar
files used to build the package in the pbuilder environment.

## Output

All output can be found in the build output of the project when it is
built.  If for some reason the build fails, this is a good first place
to look.  If there is a configuration problem, a (hopefully) useful
error message will be printed out when the build fails.  

## Building packages with 'quilt' format

When building a package with format "3.0 (quilt)", you must provide the
orig.tar.gz file for the builder to work properly.  This can be done one
of two ways: either you can provide the orig.tar.gz through a pre-build
step of some kind, or under the 'advanced' section there is a field for
you to fill in the name of the package to checkout using the
'pristine-tar' command.

## Cowbuilder vs pbuilder

This plugin allows you to use either Cowbuilder or Pbuilder to build packages.
If you are cross-compiling, using PBuilder is probably faster than using
Cowbuilder.  When cross-compiling, the rootfs will be your native architecture,
and a cross-compiler will be used to compile the code.  This depends on any
dependent packages being multiarch capable.

With multiarch-compatible packages, this means that any dependent packages
that get installed into the rootfs must install pkgconfig files into
/usr/lib/<triplet>/pkgconfig for example.

## Issue Tracking

Please file any bugs that you may find on the Jenkins JIRA, using the
debian-pbuilder-plugin component.  [Click here](https://issues.jenkins-ci.org/issues/?filter=18140)
for all open issues.
