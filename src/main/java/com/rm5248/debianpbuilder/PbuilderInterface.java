package com.rm5248.debianpbuilder;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.remoting.VirtualChannel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.logging.Logger;
import org.jenkinsci.remoting.RoleChecker;

/**
 * An interface to allow us to easily switch between using pbuilder or cowbuilder.
 */
public abstract class PbuilderInterface {
    private static final Logger LOGGER = Logger.getLogger( PbuilderInterface.class.getName() );

    protected String m_architecture;
    protected String m_distribution;
    protected Launcher m_launcher;
    protected String m_dpkgArch;
    protected String m_hookdir;
    protected PrintStream m_logger;
    protected FilePath m_pbuilderrc;
    protected FilePath m_workspace;

    /**
     * Do the build of the specified package.
     *
     * @param outputDirectory Where to place the binaries
     * @param sourceFile The .dsc to use to build
     * @param numCores How many cores to use to build.
     * @return True if the build succeeded, false otherwise
     * @throws IOException
     * @throws InterruptedException
     */
    abstract boolean buildInEnvironment( FilePath outputDirectory,
            FilePath sourceFile,
            int numCores )
            throws IOException, InterruptedException;

    abstract boolean createOrUpdateBase() throws IOException, InterruptedException;

    protected final String getDebootstrap(){
        if( m_dpkgArch.equals( m_architecture ) ){
            return "debootstrap";
        }else{
            return "qemu-debootstrap";
        }
    }

    protected final void setDpkgArchitecture() throws IOException, InterruptedException {
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

        InputStream is = proc.getStdout();
        if( is != null ){
            Scanner scan = new Scanner( is, "UTF-8" );
            m_dpkgArch = scan.nextLine();
        }
    }

    /**
     * Returns the system architecture if 'architecture' is set to 'all',
     * otherwise get the architecture to build for.
     *
     * @return
     */
    protected final String getArch(){
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
    protected final int getPID() throws IOException {
        Path link = FileSystems.getDefault().getPath( "/proc/self" );

        Path linked = Files.readSymbolicLink( link );

        return Integer.parseInt( linked.toFile().getName() );
    }

    protected static final class PbuilderConfigWriter implements FilePath.FileCallable<Void>{

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

    protected static final class CheckIfAbsolutePathExists implements FilePath.FileCallable<Boolean>{

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
