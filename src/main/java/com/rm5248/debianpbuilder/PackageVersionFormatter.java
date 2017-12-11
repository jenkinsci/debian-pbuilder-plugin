package com.rm5248.debianpbuilder;

import hudson.EnvVars;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Formats the name of a package depending on user-supplied input.
 */
public class PackageVersionFormatter {
    
    private PackageVersionFormatter(){}
    
    /**
     * Format the package version based on the format.
     * 
     * @param format The format to use
     * @param envVars The environment variables, used to get the SVN/GIT version
     * @param buildNumber The number of the build
     */
    public static String formatPackageVersion( String format, 
            EnvVars envVars,
            int buildNumber ){
        LocalDateTime localdt = LocalDateTime.now();
        
        //very stupid here, we'll basically just have a whitelist of patterns
        //that we can replace for time.
        format = format.replace( "YYYY", DateTimeFormatter.ofPattern( "YYYY").format( localdt ) );
        format = format.replace( "MM", DateTimeFormatter.ofPattern( "MM").format( localdt ) );
        format = format.replace( "dd", DateTimeFormatter.ofPattern( "dd").format( localdt ) );
        format = format.replace( "HH", DateTimeFormatter.ofPattern( "HH").format( localdt ) );
        format = format.replace( "mm", DateTimeFormatter.ofPattern( "mm").format( localdt ) );
        format = format.replace( "ss", DateTimeFormatter.ofPattern( "ss").format( localdt ) );
        
        //Replace the git/svn commit
        format = format.replace( "%rev%", getRevision( envVars ) );
        
        //Replace build number
        format = format.replace( "%build%", String.format( "%d", buildNumber ) );
        
        return format;
    }
    
    private static String getRevision( EnvVars envVars ){
        String toRet = "NOREV";
        
        if( envVars.containsKey( "GIT_COMMIT" ) ){
            toRet = "git" + envVars.get( "GIT_COMMIT" ).substring( 0, 7 );
        }

        if( envVars.containsKey( "SVN_REVISION" ) ){
            toRet = "svn" + envVars.get( "SVN_REVISION" );
        }
        
        return toRet;
    }
    
}
