package com.rm5248.debianpbuilder;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.remoting.VirtualChannel;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.logging.Logger;
import org.jenkinsci.remoting.RoleChecker;

/**
 *
 * @author robert
 */
class CowbuilderHelper {
    private static final Logger LOGGER = Logger.getLogger( CowbuilderHelper.class.getName() );
    
    private Path m_cowbuilderBase;
    private String m_architecture;
    private String m_distribution;
    private Launcher m_launcher;
    private String m_dpkgArch;
    private String m_hookdir;
    private String m_updateLockfile;
    private PrintStream m_logger;
    private FilePath m_pbuilderrc;
    private File m_pbuilderrcAsFile;
    private FilePath m_workspace;
    
    CowbuilderHelper( FilePath workspace, Launcher launcher, PrintStream logger, 
            String architecture, String distribution, String hookdir, PbuilderConfiguration pbuilderConfig ) throws IOException, InterruptedException {
        m_logger = logger;
        m_architecture = architecture;
        m_distribution = distribution;
        m_launcher = launcher;
        m_hookdir = hookdir;
        m_workspace = workspace;
        
        setDpkgArchitecture();
        
        if( m_architecture == null ){
            m_architecture = m_dpkgArch;
        }
        
        m_cowbuilderBase = FileSystems.getDefault().getPath( "/var/cache/pbuilder/base-" + m_distribution + "-" + m_architecture );
        
        String baseLockfile = "/var/run/lock/" + m_distribution + "-" + getArch();
        m_updateLockfile = baseLockfile + ".update";
                
        LOGGER.fine( "Pbuilder config: " + pbuilderConfig.toConfigFileString() );
        
        logger.println( "Pbuilder configuration: " );
        logger.println( pbuilderConfig.toConfigFileString() );
        
        if( workspace == null ){
            return;
        }
        
        m_pbuilderrc = workspace.createTempFile( "pbuilderrc", null );
        m_pbuilderrc.act( new PbuilderConfigWriter( pbuilderConfig.toConfigFileString() ) );
        m_pbuilderrcAsFile = new File( m_pbuilderrc.toURI() );
    }
    
    public boolean createOrUpdateCowbuilder() throws IOException, InterruptedException { 
        //note: this will lock on the master, is that what we want? 
        //unsure.....
        FileChannel fc = new RandomAccessFile( m_updateLockfile, "rw" ).getChannel();
        int times = 0;
        
        do{
            try( FileLock lock = fc.tryLock() ){
                if( lock == null || m_workspace == null ){
                    return false;
                }

                boolean baseExists = m_workspace.act( new CheckIfAbsolutePathExists( m_cowbuilderBase.toFile().getAbsolutePath() ) );

                if( !baseExists ){
                    return createCowbuilderBase();
                }else{
                    return updateCowbuilderBase();
                }
            }catch( OverlappingFileLockException ex ){
                LOGGER.fine( "Unable to get lock, will try again.  Reason: " + ex.getMessage() );
            }
            
            try{
                LOGGER.fine( "Unable to acquire lock, sleeping for 10 seconds..." );
                Thread.sleep( 10000 );
            }catch( InterruptedException ex ){}
        }while( ++times < 10 );
        
        return false;
    }
    
    private boolean createCowbuilderBase() throws IOException, InterruptedException {        
        ProcStarter procStarter = m_launcher
            .launch()
                .stdout( m_logger )
            .envs( getDistArchEnv() )
            .cmds( "sudo", 
                    "cowbuilder", 
                    "--create", 
                    "--basepath",
                    m_cowbuilderBase.toString(),
                    "--distribution",
                    m_distribution,
                    "--debootstrap",
                    getDebootstrap(),
                    "--architecture",
                    m_architecture,
                    "--debootstrapopts",
                    "--arch",
                    "--debootstrapopts",
                    getArch(),
                    "--debootstrapopts",
                    "--variant=buildd",
                    "--configfile",
                    m_pbuilderrcAsFile.getAbsolutePath(),
                    "--hookdir",
                    m_hookdir );
        int status = procStarter.join();
        
        if( status != 0 ){
            m_logger.println( "Unable to create cowbuilder environment(is it installed and do you have sudo privliges?)" );
            return false;
        }
        
        return true;
    }
    
    private boolean updateCowbuilderBase() throws IOException, InterruptedException {
        ProcStarter procStarter = m_launcher
            .launch()
                .stdout( m_logger )
            .envs( getDistArchEnv() )
            .cmds( "sudo", 
                    "cowbuilder", 
                    "--update",
                    "--basepath",
                    m_cowbuilderBase.toString(),
                    "--configfile",
                    m_pbuilderrcAsFile.getAbsolutePath() );
        int status = procStarter.join();
        
        if( status != 0 ){
            m_logger.println( "Unable to create cowbuilder environment(is it installed and do you have sudo privliges?)" );
            return false;
        }
        
        return true;
    }
    
    /**
     * Put DIST and ARCH into our environment for cowbuilder
     * @return 
     */
    private Map<String,String> getDistArchEnv(){
        Map<String,String> newEnv = new HashMap<String,String>();
        
        newEnv.put( "DIST", m_distribution );
        newEnv.put( "ARCH", m_architecture );
        
        return newEnv;
    }
    
    private String getDebootstrap(){
        if( m_dpkgArch.equals( m_architecture ) ){
            return "debootstrap";
        }else{
            return "qemu-debootstrap";
        }
    }
    
    private void setDpkgArchitecture() throws IOException, InterruptedException {
         Launcher.ProcStarter procStarter = m_launcher
            .launch()
            .cmds( "dpkg", "--print-architecture" )
            .readStdout();
        Proc proc = procStarter.start();
        int status;
        status = proc.join();
        
        if( status != 0 ){
            return;
        }
        
        Scanner scan = new Scanner( proc.getStdout(), "UTF-8" );
        m_dpkgArch = scan.nextLine();
    }
    
    /**
     * Returns the system architecture if 'architecture' is set to 'all', 
     * otherwise get the architecture to build for.
     * 
     * @return 
     */
    private String getArch(){
        if( m_architecture.equals( "all" ) ){
            return m_dpkgArch;
        }
        
        return m_architecture;
    }
    
    /**
     * We *could* resort to JNA here... but what's the fun in that? ;)
     * @return
     * @throws IOException 
     */
    private int getPID() throws IOException {
        Path link = FileSystems.getDefault().getPath( "/proc/self" );
        
        Path linked = Files.readSymbolicLink( link );
        
        return Integer.parseInt( linked.toFile().getName() );
    }
    
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings( value="NP_LOAD_OF_KNOWN_NULL_VALUE",
                    justification="Does not produce valid output(load of known null at end of try block)" )
    boolean buildInEnvironment( FilePath outputDirectory, FilePath sourceFile, int numCores ) throws IOException, InterruptedException {
        boolean retValue;
        
        if( outputDirectory == null || sourceFile == null ){
            m_logger.println( "Output directory or source file null.  This is a programming problem, "
                    + "please file a bug report." );
            return false;
        }
        
        File io_outputFile = new File( outputDirectory.toURI() );
        File io_sourceFile = new File( sourceFile.toURI() );
            
        retValue = doBuild( io_outputFile.getAbsolutePath(), 
                io_sourceFile.getAbsolutePath(),
                numCores );
        
        return retValue;
    }
    
    private boolean doBuild( String outputDir, String sourceFile, int numCores ) throws IOException, InterruptedException {  
        String debBuildOpts = "-sa";
        String bindMounts;
        String jLevel;
              
        if( numCores == -1 ){
            jLevel = "-jauto";
        }else if( numCores > 0 ){
            jLevel = String.format( "-j%d", numCores );
        }else{
            m_logger.println( "Unable to use cores of " + numCores + ": must be either -1 or a positive integer" );
            return false;
        }
               
        ProcStarter procStarter = m_launcher
            .launch()
                .stdout( m_logger )
            .envs( getDistArchEnv() )
            .cmds( "sudo", 
                    "cowbuilder", 
                    "--buildresult", 
                    outputDir,
                    "--build",
                    sourceFile,
                    "--basepath",
                    m_cowbuilderBase.toString(),
                    "--debbuildopts",
                    debBuildOpts,
                    "--debbuildopts",
                    jLevel,
                    "--hookdir",
                    m_hookdir,
                    "--configfile",
                    m_pbuilderrcAsFile.getAbsolutePath() );
        int status = procStarter.join();
        
        if( status != 0 ){
            return false;
        }
        
        return true;
    }
    
    private static final class PbuilderConfigWriter implements FileCallable<Void>{
        
        private static final long serialVersionUID = 1L;
        
        private String m_toWrite;
        
        public PbuilderConfigWriter( String toWrite ){
            m_toWrite = toWrite;
            LOGGER.finer( "Pbuilder config in config writer: " + m_toWrite );
        }

        @Override
        public Void invoke( File file, VirtualChannel vc ) throws IOException, InterruptedException {
            try( Writer w = new OutputStreamWriter( new FileOutputStream( file ), "UTF-8" ) ){
                w.write(  m_toWrite );
            }
            
            return null;
        }

        @Override
        public void checkRoles( RoleChecker rc ) throws SecurityException {
            //throw new UnsupportedOperationException( "Not supported yet." ); //To change body of generated methods, choose Tools | Templates.
        }
        
    }
    
    private static final class CheckIfAbsolutePathExists implements FileCallable<Boolean>{
        
        private final String m_path;
        
        public CheckIfAbsolutePathExists( String path ){
            m_path = path;
        }
        
        @Override
        public Boolean invoke( File file, VirtualChannel vc ) throws IOException, InterruptedException {
            File f = new File( m_path );
            
            return f.exists();
        }

        @Override
        public void checkRoles( RoleChecker rc ) throws SecurityException {
            //throw new UnsupportedOperationException( "Not supported yet." ); //To change body of generated methods, choose Tools | Templates.
        }
    }
}
