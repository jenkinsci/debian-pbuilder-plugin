package com.rm5248.debianpbuilder;

/**
 * Represents the pbuilder configuration.
 * 
 * See man pbuilderrc for all of the possible confgi options
 */
class PbuilderConfiguration {
    
    private boolean m_useNetwork;
    private String m_debootstrap;
    private String m_mirrorSite;
    private String[] m_debootstrapOpts;
    private boolean m_useEatMyData;
    private String[] m_extraPackages;
    private String[] m_additionalBuild;
    
    PbuilderConfiguration(){
        m_useNetwork = false;
        m_useEatMyData = false;
    }
    
    void setNetwork( boolean network ){
        m_useNetwork = network;
    }
    
    void setDebootstrap( String debootstrap ){
        m_debootstrap = debootstrap;
    }
    
    void setMirrorSite( String mirrorSite ){
        m_mirrorSite = mirrorSite;
    }
    
    void setDebootstrapOpts( String ... opts ){
        m_debootstrapOpts = opts;
    }
    
    void setUseEatMyData( boolean eatMyData ){
        m_useEatMyData = eatMyData;
    }
    
    void setExtraPackages( String ... extra ){
        m_extraPackages = extra;
    }
    
    void setAdditionalBuildResults( String ... buildResults ){
        m_additionalBuild = buildResults;
    }
    
    String toConfigFileString(){
        StringBuilder sb = new StringBuilder();
        
        sb.append( "USENETWORK=" );
        if( m_useNetwork ){
            sb.append( "yes" );
        }else{
            sb.append( "no" );
        }
        sb.append( "\n" );
        
        if( m_debootstrap != null ){
            sb.append( "DEBOOTSTRAP=" );
            sb.append( m_debootstrap );
            sb.append( "\n" );
        }
        
        if( m_mirrorSite != null ){
            sb.append( "MIRRORSITE=" );
            sb.append( m_mirrorSite );
            sb.append( "\n" );
        }
        
        if( m_debootstrapOpts != null ){
            sb.append( "DEBOOTSTRAPOPTS=(" );
            for( int x = 0; x < m_debootstrapOpts.length; x++ ){
                sb.append( '\'' );
                sb.append( m_debootstrapOpts[ x ] );
                sb.append( '\'' );
                sb.append( ' ' );
            }
            sb.append( ")\n" );
        }
        
        if( m_useEatMyData ){
            if( m_extraPackages == null ){
                m_extraPackages = new String[ 1 ];
                m_extraPackages[ 0 ] = "eatmydata";
            }
            sb.append( "EATMYDATA=yes\n" );
            sb.append( "export LD_PRELOAD=libeatmydata.so\n" );
        }
        
        if( m_extraPackages != null ){
            sb.append( "EXTRAPACKAGES=" );
            for( int x = 0; x < m_extraPackages.length; x++ ){
                sb.append( m_extraPackages[ x ] );
                sb.append( " " );
            }
            sb.append( "\n" );
        }
        
        if( m_additionalBuild != null && m_additionalBuild.length > 0 ){
            sb.append( "ADDITIONAL_BUILDRESULTS=(" );
            for( int x = 0; x < m_additionalBuild.length; x++ ){
                sb.append( "\"" );
                sb.append( m_additionalBuild[ x ] );
                sb.append( "\"" );
                sb.append( " " );
            }
            sb.append( ")\n" );
        }
        
        return sb.toString();
    }
}
