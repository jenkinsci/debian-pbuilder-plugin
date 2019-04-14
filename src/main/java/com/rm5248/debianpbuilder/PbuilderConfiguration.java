package com.rm5248.debianpbuilder;

/**
 * Represents the pbuilder configuration.
 * 
 * See man pbuilderrc for all of the possible config options
 */
class PbuilderConfiguration {
    
    enum SatisfyDependsResolver {
        APT("pbuilder-satsifydepends-apt"),
        EXPERIMENTAL("pbuilder-satisfydepends-experimental"),
        APTITUDE("pbuilder-satisfydepends-aptitude"),
        GDEBI("pbuilder-satisfydepends-gdebi"),
        CLASSIC("pbuilder-satisfydepends-classic"),
        DEFAULT("")
        ;
        
        private final String m_resolverName;
         
        private SatisfyDependsResolver(String name){
            m_resolverName = name;
        }
        
        public String getResolverName(){
            return m_resolverName;
        }
    }
    
    private boolean m_useNetwork;
    private String m_debootstrap;
    private String m_mirrorSite;
    private String[] m_debootstrapOpts;
    private boolean m_useEatMyData;
    private String[] m_extraPackages;
    private String[] m_additionalBuild;
    private String m_components;
    private SatisfyDependsResolver m_satisfyDependsCommand;
    
    PbuilderConfiguration(){
        m_useNetwork = false;
        m_useEatMyData = false;
        m_satisfyDependsCommand = SatisfyDependsResolver.DEFAULT;
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

    void setComponents( String components ){
        m_components = components;
    }
    
    void setSatisfyDependsCommand( SatisfyDependsResolver depends ){
        m_satisfyDependsCommand = depends;
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

        if( m_components != null && m_components.length() > 0 ){
            sb.append( "COMPONENTS=\"" );
            sb.append( m_components );
            sb.append( "\"\n" );
        }
        
        if( m_satisfyDependsCommand != SatisfyDependsResolver.DEFAULT ){
            sb.append("PBUILDERSATISFYDEPENDSCMD=");
            sb.append( m_satisfyDependsCommand.getResolverName() );
            sb.append("\n");
        }
        
        return sb.toString();
    }
}
