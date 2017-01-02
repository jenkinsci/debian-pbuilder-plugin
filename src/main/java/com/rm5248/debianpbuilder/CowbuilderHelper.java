package com.rm5248.debianpbuilder;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.model.AbstractBuild;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 *
 * @author robert
 */
class CowbuilderHelper {
    
    private Path m_cowbuilderBase;
    private String m_architecture;
    private String m_distribution;
    private Launcher m_launcher;
    private String m_dpkgArch;
    private AbstractBuild m_build;
    private String m_hookdir;
    private String m_buildLockfile;
    private String m_updateLockfile;
    private String m_updateLockfilePid;
    private PrintStream m_logger;
    
    CowbuilderHelper( AbstractBuild build, Launcher launcher, PrintStream logger, 
            String architecture, String distribution, String hookdir ) throws IOException, InterruptedException {
        m_logger = logger;
        m_architecture = architecture;
        m_distribution = distribution;
        m_launcher = launcher;
        m_build = build;
        m_hookdir = hookdir;
        
        setDpkgArchitecture();
        
        if( m_architecture == null ){
            m_architecture = m_dpkgArch;
        }
        
        m_cowbuilderBase = FileSystems.getDefault().getPath( "/var/cache/pbuilder/base-" + m_distribution + "-" + m_architecture );
        
        String baseLockfile = "/var/run/lock/" + m_distribution + "-" + getArch();
        m_buildLockfile = baseLockfile + ".building." + getPID();
        m_updateLockfile = baseLockfile + ".update";
        m_updateLockfilePid = baseLockfile + ".update." + getPID();
    }
    
    public boolean createOrUpdateCowbuilder() throws IOException, InterruptedException {         
        FileChannel fc = new RandomAccessFile( m_updateLockfile, "rw" ).getChannel();
        try( FileLock lock = fc.tryLock() ){
            if( lock == null ){
                return false;
            }
            
            if( !Files.exists( m_cowbuilderBase ) ){
                return createCowbuilderBase();
            }else{
                return updateCowbuilderBase();
            }
        }
    }
    
    private boolean createCowbuilderBase() throws IOException, InterruptedException {
        FilePath pbuilderrc = m_build.getWorkspace().createTempFile( "pbuilderrc", null );
        File pbuilderAsFile = new File( pbuilderrc.toURI() );
        
        Proc proc = null;
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
                    "--variant=buildd",
                    "--configfile",
                    pbuilderAsFile.getAbsolutePath(),
                    "--hookdir",
                    m_hookdir );
        proc = procStarter.start();
        int status = procStarter.join();
        
        if( status != 0 ){
            m_logger.println( "Unable to create cowbuilder environment(is it installed and do you have sudo privliges?)" );
            return false;
        }
        
        return true;
    }
    
    private boolean updateCowbuilderBase() throws IOException, InterruptedException {
        FilePath pbuilderrc = m_build.getWorkspace().createTempFile( "pbuilderrc", null );
        File pbuilderAsFile = new File( pbuilderrc.toURI() );
        
        Proc proc = null;
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
                    pbuilderAsFile.getAbsolutePath() );
        proc = procStarter.start();
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
        int status;
        Proc proc = null;
        proc = procStarter.start();
        status = procStarter.join();
        
        if( status != 0 ){
            return;
        }
        
        Scanner scan = new Scanner( proc.getStdout() );
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
    
    boolean buildInEnvironment( FilePath outputDirectory, FilePath sourceFile, int numCores ) throws IOException, InterruptedException {
        FileChannel fc = new RandomAccessFile( m_buildLockfile, "rw" ).getChannel();
        try( FileLock lock = fc.tryLock() ){
            if( lock == null ){
                return false;
            }
            
            return doBuild( new File( outputDirectory.toURI() ).getAbsolutePath(), 
                    new File( sourceFile.toURI() ).getAbsolutePath(),
                    numCores );
        }
    }
    
    private boolean doBuild( String outputDir, String sourceFile, int numCores ) throws IOException, InterruptedException {
        FilePath pbuilderrc = m_build.getWorkspace().createTempFile( "pbuilderrc", null );
        String debBuildOpts = "-sa";
        String bindMounts;
        String jLevel = String.format( "-j%d", numCores );
               
        Proc proc = null;
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
                    new File( pbuilderrc.toURI() ).getAbsolutePath() );
        proc = procStarter.start();
        int status = procStarter.join();
        
        if( status != 0 ){
            return false;
        }
        
        return true;
    }
}
