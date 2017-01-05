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
    
    PbuilderConfiguration(){
        m_useNetwork = false;
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
        
        return sb.toString();
    }
}
