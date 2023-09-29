package com.rm5248.debianpbuilder;

import hudson.FilePath;
import hudson.Launcher;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 *
 * @author robert
 */
public class PbuilderHelper extends PbuilderInterface {
    private static final Logger LOGGER = Logger.getLogger( PbuilderHelper.class.getName() );

    private Path m_pbuilderBaseTgz;
    private String m_updateLockfile;

    PbuilderHelper( FilePath workspace,
            Launcher launcher,
            PrintStream logger,
            String architecture,
            String distribution,
            String hookdir,
            PbuilderConfiguration pbuilderConfig ) throws IOException, InterruptedException {
        m_logger = logger;
        m_hostArch = architecture;
        m_distribution = distribution;
        m_launcher = launcher;
        m_hookdir = hookdir;
        m_workspace = workspace;

        setBuildArch();

        if( m_hostArch == null ){
            m_hostArch = m_buildArch;
        }

        m_pbuilderBaseTgz = FileSystems.getDefault().getPath( "/var/cache/pbuilder/base-" + m_distribution + "-" + m_hostArch + ".tgz" );

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
    }

    @Override
    boolean buildInEnvironment(FilePath outputDirectory, FilePath sourceFile, int numCores) throws IOException, InterruptedException {
        boolean retValue;

        if( outputDirectory == null || sourceFile == null ){
            m_logger.println( "Output directory or source file null.  This is a programming problem, "
                    + "please file a bug report." );
            return false;
        }

        retValue = doBuild( outputDirectory.getName(),
                sourceFile.getName(),
                numCores );

        return retValue;
    }

    private boolean doBuild( String outputDir, String sourceFile, int numCores ) throws IOException, InterruptedException {
        String debBuildOpts = "-sa";
        String jLevel;

        if( numCores == -1 ){
            jLevel = "-jauto";
        }else if( numCores > 0 ){
            jLevel = String.format( "-j%d", numCores );
        }else{
            m_logger.println( "Unable to use cores of " + numCores + ": must be either -1 or a positive integer" );
            return false;
        }

        Launcher.ProcStarter procStarter = m_launcher
            .launch()
            .pwd(m_workspace)
            .stdout( m_logger )
            .cmds( "sudo",
                    "pbuilder",
                    "build",
                    "--architecture",
                    m_buildArch,
                    "--host-arch",
                    m_hostArch,
                    "--configfile",
                    m_pbuilderrc.getName(),
                    "--basetgz",
                    m_pbuilderBaseTgz.toString(),
                    "--distribution",
                    m_distribution,
                    "--debbuildopts",
                    debBuildOpts,
                    "--debbuildopts",
                    jLevel,
                    "--hookdir",
                    m_hookdir,
                    "--configfile",
                    m_pbuilderrc.getName(),
                    "--buildresult",
                    outputDir,
                    sourceFile );
        int status = procStarter.join();

        if( status != 0 ){
            return false;
        }

        return true;
    }

    @Override
    boolean createOrUpdateBase() throws IOException, InterruptedException {
        boolean baseExists = m_workspace.act( new CheckIfAbsolutePathExists( m_pbuilderBaseTgz.toFile().getAbsolutePath() ) );

        // Note: because this is not by any means an atomic operation, this could fail
        // in the event that:
        // 1. No base exists and two jobs start at the same time, in which case
        //    the first one will succeed and the second one will fail
        // Since the above only has to happen once, this is unlikely to cause
        // issues that we really care about.
        // Multiple updates should be fine, as one must finish before another
        // one can do the update, and the COW functionality means it shouldn't
        // cause an issue(?)
        if( !baseExists ){
            return createPbuilderBase();
        }else{
            return updatePbuidlerBase();
        }
    }

    private boolean createPbuilderBase() throws IOException, InterruptedException {
        Launcher.ProcStarter procStarter = m_launcher
            .launch()
            .pwd(m_workspace)
            .stdout( m_logger )
            .cmds("flock",
                    "-n",
                    m_updateLockfile,
                    "sudo",
                    "pbuilder",
                    "create",
                    "--architecture",  // When we create the pbuilder base, use host arch both times?
                    m_hostArch,
                    "--host-arch",
                    m_hostArch,
                    "--configfile",
                    m_pbuilderrc.getName(),
                    "--basetgz",
                    m_pbuilderBaseTgz.toString(),
                    "--distribution",
                    m_distribution );
        int status = procStarter.join();

        if( status != 0 ){
            m_logger.println( "Unable to create cowbuilder environment(is it installed and do you have sudo privliges?)" );
            return false;
        }

        return true;
    }

    private boolean updatePbuidlerBase() throws IOException, InterruptedException {
        Launcher.ProcStarter procStarter = m_launcher
            .launch()
            .pwd(m_workspace)
            .stdout( m_logger )
            .cmds( "flock",
                    "-n",
                    m_updateLockfile,
                    "sudo",
                    "pbuilder",
                    "update",
                    "--distribution",
                    m_distribution,
                    "--basetgz",
                    m_pbuilderBaseTgz.toString(),
                    "--configfile",
                    m_pbuilderrc.getName() );
        int status = procStarter.join();

        if( status != 0 ){
            m_logger.println( "Unable to create cowbuilder environment(is it installed and do you have sudo privliges?)" );
            return false;
        }

        return true;
    }

}
