package com.rm5248.debianpbuilder;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
import hudson.Proc;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.model.Project;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jenkins.model.ArtifactManager;
import jenkins.tasks.SimpleBuildStep;
import jenkins.util.BuildListenerAdapter;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Jenkins plugin that builds Debian packages in a pbuilder/cowbuilder environement.
 * 
 * Based off of: https://jenkins-debian-glue.org/
 * 
 * The reason for having this as an actual plugin instead of the scripts is so that
 * we can have builders on different machines that can all communicate back to the 
 * master Jenkins instance.
 */
public class DebianPbuilder extends Builder implements SimpleBuildStep {

    private int numberCores;
    private String distribution;
    private String mirrorSite;
    private boolean buildAsTag;
    private String additionalBuildResults;
    private String architecture;
    private String debianDirLocation;
    
    @DataBoundConstructor
    public DebianPbuilder(){
        numberCores = 1;
    }

    @Deprecated
    public DebianPbuilder(int numberCores, 
            String distribution, 
            String mirrorSite, 
            boolean buildAsTag,
            String additionalBuildResults,
            String architecture) {
        this.numberCores = numberCores;
        this.distribution = distribution;
        this.mirrorSite = mirrorSite;
        this.buildAsTag = buildAsTag;
        this.additionalBuildResults = additionalBuildResults;
        this.architecture = architecture;
    }
    
    @DataBoundSetter
    public void setNumberCores( int cores ){
        numberCores = cores;
    }
    
    @DataBoundSetter
    public void setDistribution( String distribution ){
        this.distribution  = distribution;
    }
    
    @DataBoundSetter
    public void setMirrorSite( String mirrorSite ){
        this.mirrorSite = mirrorSite;
    }
    
    @DataBoundSetter
    public void setBuildAsTag( boolean buildAsTag ){
        this.buildAsTag = buildAsTag;
    }
    
    @DataBoundSetter
    public void setAdditionalBuildResults( String additionalBuildResults ){
        this.additionalBuildResults = additionalBuildResults;
    }
    
    @DataBoundSetter
    public void setArchitecture( String architecture ){
        this.architecture = architecture;
    }
    
    @DataBoundSetter
    public void setDebianDirLocation( String debianDirLocation ){
        this.debianDirLocation = debianDirLocation;
    }

    public int getNumberCores(){
        return numberCores;
    }
    
    public String getDistribution(){
        return distribution;
    }
    
    public String getMirrorSite(){
        return mirrorSite;
    }
    
    public boolean getBuildAsTag(){
        return buildAsTag;
    }
    
    public String getAdditionalBuildResults(){
        return additionalBuildResults;
    }
    
    public String getArchitecture(){
        return architecture;
    }
    
    public String getDebianDirLocation(){
        if( this.debianDirLocation == null || 
           this.debianDirLocation.length() == 0 ){
            return getDescriptor().getDefaultDebianDirLocation();
        }
        
        return debianDirLocation;
    }
    
    @Override
    public void perform(Run<?,?> run, FilePath workspace, Launcher launcher, TaskListener listener )
            throws InterruptedException, IOException {
        boolean success = doTheBuild( run, workspace, launcher, listener );
        
        if( !success ){
            throw new AbortException( "Unable to build properly" );
        }
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) 
            throws InterruptedException, IOException {
        
        return doTheBuild( build, build.getWorkspace(), launcher, listener );
    }
    
    private boolean doTheBuild( Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener )
            throws InterruptedException, IOException {
        String architecture = null;
        String snapshotVersion;
        CowbuilderHelper cowHelp;
        FilePath binariesLocation;
        FilePath dscFile = null;
        FilePath hookdir = null;
        PbuilderConfiguration pbuildConfig = new PbuilderConfiguration();
        EnvVars envVars = build.getEnvironment(listener);
        
        if( !launcher.isUnix() ){
            listener.getLogger().println( "Can't build: not on Unix-like system" );
            return false;
        }
        
        if( workspace == null ){
            listener.getLogger().println( "Can't build: workspace is  null" );
            return false;
        }
        
        if( !ensureDpkgParseChangelogIsValid(launcher, listener) ){
            return false;
        }
        
        if( !isDebianPackage(workspace, launcher, listener) ){
            listener.getLogger().println( "We do not appear to have an actual package" );
            return false;
        }
        
        String packageName = getDpkgField(workspace, launcher, listener, "source" );
        if( packageName == null ){
            listener.getLogger().println( "Unable to get package name(source) from changelog" );
            return false;
        }
        
        String version = getDpkgField(workspace, launcher, listener, "version" );
        if( version == null ){
            listener.getLogger().println( "Unable to get version from changelog" );
            return false;
        }
        
        String distribution = getDpkgField(workspace, launcher, listener, "distribution" );
        if( distribution == null ){
            listener.getLogger().println( "Unable to get distribution from changelog" );
            return false;
        }
        
        architecture = getActualArchitecture( build, listener );
        if( architecture != null && architecture.length() == 0 ){
            listener.getLogger().println( "Architecture is 0-length string: using dpkg default");
            architecture = null;
        }
        
        boolean isTag = checkIfBuildingTag( envVars );
        
        if( !isTag ){
            //we are not building a tag, update the version appropriately
            
            if( distribution.equalsIgnoreCase( "unreleased" ) ){
                //do not raise the version number if this is an unreleased version
                snapshotVersion = version + "~";
            }else{
                snapshotVersion = version + "+0";
            }
            snapshotVersion += PackageVersionFormatter.formatPackageVersion( 
                    getDescriptor().getPackageVersionFormat(), envVars, build.getNumber() );

            listener.getLogger().println( "Snapshot version: " + snapshotVersion );


            updateChangelog(launcher, workspace.child( getDebianDirLocation() ).child( "debian" ).child( "changelog" ),
                    packageName, snapshotVersion);
        }else{
            //we are building a tagged version, don't update the changelog or version
            snapshotVersion = version;
        }

        if( !tarSources(workspace, launcher, listener) ){
            return false;
        }

        generateChanges(workspace, launcher, listener, packageName, snapshotVersion);
        
        binariesLocation = workspace.createTempDir( "binaries", null );
        hookdir = workspace.child( "hookdir" );
        if( !hookdir.exists() ){
            hookdir.mkdirs();
        }
        
        //make sure any files in the hookdir are executable
        for( FilePath path : hookdir.list() ){
            path.chmod( 0755 );
        }
        
        for( FilePath path : workspace.list() ){
            if( path.getName().endsWith( ".dsc" ) ){
                if( dscFile != null ){
                    listener.getLogger().println( "More than one dsc file found, aborting build" );
                    return false;
                }
                dscFile = path;
            }
        }
        
        if( distribution.equalsIgnoreCase( "UNRELEASED" ) ){
            distribution = getStdoutOfProcess(workspace, launcher, listener, "lsb_release", "--short", "--codename" );
            if( distribution == null ){
                distribution = "sid";
            }
        }
        
        //user provided a distribution, override automatic settings
        if( this.distribution != null && this.distribution.length() > 0 ){
            distribution = this.distribution;
        }
        
        pbuildConfig.setNetwork( true );
        if( additionalBuildResults != null && additionalBuildResults.length() > 0 ){
            pbuildConfig.setAdditionalBuildResults( additionalBuildResults.split( "," ) );
        }
        
        if( isUbuntu( workspace, launcher, listener ) ){
            pbuildConfig.setDebootstrapOpts( "--keyring", "/usr/share/keyrings/debian-archive-keyring.gpg" );
        }
        
        if( mirrorSite != null && mirrorSite.length() > 0 ){
            pbuildConfig.setMirrorSite( mirrorSite );
        }
        
        //Now that we have our sources, run debootstrap
        cowHelp = new CowbuilderHelper(workspace, launcher, listener.getLogger(),
                architecture, distribution, 
                new File( hookdir.toURI() ).getAbsolutePath(),
                pbuildConfig);
        
        if( !cowHelp.createOrUpdateCowbuilder() ){
            return false;
        }
        
        if( !cowHelp.buildInEnvironment( binariesLocation, dscFile, numberCores ) ){
            return false;
        }
        
        
        Map<String,String> files = new HashMap<String,String>();
        for( FilePath path : binariesLocation.list() ){
            files.put( path.getName(), path.getName() );
        }
        
        BuildListenerAdapter bl = new BuildListenerAdapter( listener );
        build.pickArtifactManager().archive( binariesLocation, launcher, bl, files );
                
        return true;
    }
    
    private String getActualArchitecture( Run<?,?> build, TaskListener listener )
            throws InterruptedException, IOException {
        if( build instanceof AbstractBuild ){
            EnvVars env = build.getEnvironment( listener );
            
            if( !env.containsKey( "architecture" ) ){
                if( architecture != null && architecture.length() > 0 ){
                    listener.getLogger().println( "No architecture variable in environment"
                            + " found, using parameter instead." );
                    return architecture;
                }else{
                    listener.getLogger().println( "No architecture found, using " +
                            "dpkg default" );
                    return null;
                }
            }
            
            return env.get( "architecture" );
        }else{
            return architecture;
        }
    }
    
    /**
     * Ensure that dpkg-parsechangelog is installed and >= 1.17
     * @return 
     */
    private boolean ensureDpkgParseChangelogIsValid(Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        Launcher.ProcStarter procStarter = launcher
            .launch()
            .cmdAsSingleString( "dpkg-parsechangelog --version" )
            .readStdout();
        Proc proc = procStarter.start();
        proc.join();
        
        Scanner scan = new Scanner( proc.getStdout(), "UTF-8" );
        
        String line = scan.nextLine();
        
        String pattern = ".*version (\\d+)\\.(\\d+)\\.(\\d+)";
        Matcher m = Pattern.compile( pattern ).matcher( line );
        
        if( m.find() ){
            int major = Integer.parseInt( m.group( 1 ) );
            int minor = Integer.parseInt( m.group( 2 ) );
            //int patch = Integer.parseInt( m.group( 3 ) );
            
            return ( major >= 1 && minor >= 17 );
        }
        
        listener.getLogger().println( "Can't continue: dpkg-parsechangelog is "
                + "not new enough(needs to be at least 1.17.0)" );
        
        return false;
    }
    
    /**
     * Basic check to see if we are actually trying to build a debian package.
     * 
     * @param build
     * @param launcher
     * @param listener
     * @return 
     */
    private boolean isDebianPackage( FilePath workspace, Launcher launcher, TaskListener listener ) throws IOException, InterruptedException{
        if( workspace == null ){
            return false;
        }
        Launcher.ProcStarter procStarter = launcher
            .launch()
            .pwd( workspace.child( getDebianDirLocation() ) )
            .cmdAsSingleString( "dpkg-parsechangelog --count 1" )
            .readStdout();
        int status = procStarter.join();

        if( status != 0 ){
            return false;
        }

        return true;
    }
    
    private String getDpkgField( FilePath workspace, Launcher launcher, TaskListener listener, String fieldName ) throws IOException, InterruptedException {
        if( workspace == null ){
            return null;
        }
        Launcher.ProcStarter procStarter = launcher
            .launch()
            .pwd( workspace.child( getDebianDirLocation() ) )
            .cmds( "dpkg-parsechangelog", "--show-field", fieldName )
            .readStdout();
        Proc proc = procStarter.start();
        
        int status = proc.join();
        
        if( status != 0 ){
            return null;
        }
        
        Scanner scan = new Scanner( proc.getStdout(), "UTF-8" );
        
        return scan.nextLine();
    }
    
    /**
     * Force update the changelog.  Don't use any of the debian scripts to update,
     * since we just want new stuff + old.  This is what jenkins-debian-glue does with SVN snapshots.
     * 
     */
    private void updateChangelog( Launcher launcher, FilePath changelog, String packageName, String snapshotVersion ) throws IOException, InterruptedException {
        Scanner scan = new Scanner( changelog.read(), "UTF-8" );
        StringBuilder strBuild = new StringBuilder();
        String debEmail = "Debian Pbuilder Autobuilder <" +
                getEmail(launcher) + ">";
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now();
        DateTimeFormatter dtFormat = DateTimeFormatter.ofPattern( "ccc, dd MMM YYYY HH:mm:ss Z");
                
        strBuild.append( packageName );
        strBuild.append( " (" );
        strBuild.append( snapshotVersion );
        strBuild.append( ") UNRELEASED; urgency=low" );
        strBuild.append( System.lineSeparator() );
        strBuild.append( System.lineSeparator() );
        
        strBuild.append( "  ** SNAPSHOT Build **" );
        strBuild.append( System.lineSeparator() );
        strBuild.append( System.lineSeparator() );
        
        strBuild.append( " -- " );
        strBuild.append( debEmail );
        strBuild.append( "  " );
        strBuild.append( now.format( dtFormat ) );
        strBuild.append( System.lineSeparator() );
        strBuild.append( System.lineSeparator() );
        
        while( scan.hasNextLine() ){
            strBuild.append( scan.nextLine() );
            strBuild.append( System.lineSeparator() );
        }
        
        scan.close();
                
        Writer w = new OutputStreamWriter( changelog.write(), "UTF-8" );
        w.write( strBuild.toString() );
        w.close();
    }
    
    private String getEmail( Launcher launcher ) throws IOException, InterruptedException{
        String email = getDescriptor().getJenkinsEmail();
        
        if( email == null || email.length() == 0 ){
            Launcher.ProcStarter procStarter = launcher
                .launch()
                .cmds( "hostname" )
                .readStdout();
            Proc proc = procStarter.start();
            proc.join();
            Scanner scan = new Scanner( proc.getStdout(), "UTF-8" );
            
            return "jenkins@" + scan.nextLine();
        }
        
        return email;
    }
    
    private boolean tarSources( FilePath workspace, Launcher launcher, TaskListener listener ) 
        throws IOException, InterruptedException{
        Launcher.ProcStarter procStarter = launcher
            .launch()
            .pwd( workspace )
            .cmds( "dpkg-source", "-b", getDebianDirLocation() )
            .stderr( listener.getLogger() )
            .stdout( listener.getLogger() );
        int status = procStarter.join();
        
        if( status != 0 ){
            return false;
        }
        
        return true;
    }
    
    
    
    private boolean generateChanges( FilePath workspace, Launcher launcher, TaskListener listener, 
            String packageName, String packageVersion ) throws IOException, InterruptedException {
        Launcher.ProcStarter procStarter = launcher
            .launch()
            .pwd( workspace )
            .cmds( "dpkg-genchanges", "-u.", getDebianDirLocation() )
            .stderr( listener.getLogger() )
            .stdout( listener.getLogger() );
        int status;
        Proc proc  = procStarter.start();
        status = proc.join();
        
        if( status != 0 ){
            return false;
        }
        
        if( workspace == null ){
            return false;
        }
        
        try( Scanner scan = new Scanner( proc.getStdout(), "UTF-8" );
            Writer w = new OutputStreamWriter( workspace.child( packageName + "_" + packageVersion ).write(), 
                "UTF-8" );
                ){
            while( scan.hasNextLine() ){
                w.write( scan.nextLine() );
                w.write( System.lineSeparator() );
            }
        }
        
        return true;
    }
    
    private String getStdoutOfProcess( FilePath workspace, Launcher launcher, TaskListener listener, String ... args )
        throws IOException, InterruptedException {
        StringBuilder toRet = new StringBuilder();
        Launcher.ProcStarter procStarter = launcher
            .launch()
            .pwd( workspace )
            .cmds( args )
            .readStdout();
        int status;
        Proc proc = null;
        proc = procStarter.start();
        status = proc.join();
        
        if( status != 0 ){
            return null;
        }
        
        Scanner scan = new Scanner( proc.getStdout(), "UTF-8" );
        while( scan.hasNextLine() ){
            toRet.append( scan.nextLine() );
        }
        
        return toRet.toString();
    }
    
    private boolean isUbuntu( FilePath workspace, Launcher launcher, TaskListener listener ) throws IOException, InterruptedException {
        String output = getStdoutOfProcess( workspace, launcher, listener, "lsb_release", "--id" );
        
        if( output.indexOf( "Ubuntu" ) >= 0 ){
            return true;
        }else{
            return false;
        }
    }
    
    private boolean checkIfBuildingTag( EnvVars envVars ){
        if( buildAsTag ){
            return true;
        }
        
        if( envVars.containsKey( "SVN_URL_1" ) ){
            if( envVars.get( "SVN_URL_1" ).indexOf( "tags/" ) >= 0 ){
                return true;
            }
        }
        
        if( envVars.containsKey( "GIT_BRANCH" ) ){
            if( envVars.get( "GIT_BRANCH" ).indexOf( "tags/" ) >= 0 ){
                return true;
            }
        }
        
        if( envVars.containsKey( "DEB_PBUILDER_BUILDING_TAG" ) ){
            return Boolean.parseBoolean( envVars.get( "DEB_BUILDER_BUILDING_TAG" ) );
        }
        
        return false;
    }
    
//    private String getArchitecture( Run<?,?> build, TaskListener listener ) 
//            throws InterruptedException, IOException {
//        if( build instanceof AbstractBuild ){
//            EnvVars env = build.getEnvironment( listener );
//            env.overrideAll( ((AbstractBuild)build).getBuildVariables() );
//            return env.expand( "architecture" );
//        }else{
//            return 
//        }
//    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link DebianPbuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     */
    @Symbol( "debianPbuilder" )
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        
        private String jenkinsEmail;
        private String packageVersionFormat;
        private String defaultDebianDirLocation;

        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         */
        public FormValidation doCheckName(@QueryParameter String value)
                throws IOException, ServletException {
            return FormValidation.ok();
        }
        
        public FormValidation doCheckNumberCores(@QueryParameter String value ){
            int i;
            try{
                i = Integer.parseInt( value );
            }catch( NumberFormatException ex ){
                return FormValidation.error( "That is not a valid number" );
            }
            
            if( i == 0 ){
                return FormValidation.error( "Number of cores cannot be 0" );
            }
            
            if( i < 0 && i != -1 ){
                return FormValidation.error( "Use -1 to use all available cores" );
            }
            
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Debian Pbuilder";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            jenkinsEmail = formData.getString( "jenkinsEmail" );
            packageVersionFormat = formData.getString( "packageVersionFormat" );
            defaultDebianDirLocation = formData.getString( "defaultDebianDirLocation" );
            save();
            return super.configure(req,formData);
        }
        
        public String getJenkinsEmail(){
            return jenkinsEmail;
        }
        
        public String getPackageVersionFormat(){
            if( packageVersionFormat == null || packageVersionFormat.length() == 0 ){
                return defaultPackageVersionFormat();
            }
            
            return packageVersionFormat;
        }
        
        public String defaultPackageVersionFormat(){
            return "YYYYMMddHHmmss.%rev%.%build%";
        }
        
        public String getDefaultDebianDirLocation(){
            if( defaultDebianDirLocation == null || defaultDebianDirLocation.length() == 0 ){
                return defaultDebDirLocation();
            }
            
            return defaultDebianDirLocation;
        }
        
        public String defaultDebDirLocation(){
            return "source";
        }
    }
}

