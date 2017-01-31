package com.d20pro.plugin.stock.foure;

import java.io.File;
import java.text.DateFormat;
import java.util.*;

import org.w3c.dom.*;

import com.d20pro.plugin.api.*;
import com.mindgene.common.util.FileFilterForExtension;
import com.mindgene.d20.common.creature.CreatureTemplate;

/**
 * Import WotC XML into Creatures.
 * 
 * @author ogexam
 * @author thraxxis
 */
public class CreatureImportPlugin_WotC implements ImportCreaturePlugin, XMLToDocumentHelperStrategy
{
	
  public String getPluginName()
  {
    return "DDI";
  }

 /* public static Map<String, Map<String, CreatureAttributeImportStrategy>> buildDomainStrategies( String gameSystem )
  {
    Map<String, Map<String, CreatureAttributeImportStrategy>> strategiesByDomain = new HashMap<String, Map<String, CreatureAttributeImportStrategy>>();

    strategiesByDomain.put( null, WotCImportLogic.buildDefaultStrategies() );

    Map<String, CreatureAttributeImportStrategy> attributesDomain = new HashMap<String, CreatureAttributeImportStrategy>();
    for( byte i = 0; i < D20Rules.Ability.NAMES.length; i++ )
    {
      attributesDomain.put( "a" + D20Rules.Ability.NAMES[i], new WotCImportLogic.AttributeLogic( i ) );
    }
    strategiesByDomain.put( "attributes", attributesDomain );

    HashMap<String, CreatureAttributeImportStrategy> savesDomain = new HashMap<String, CreatureAttributeImportStrategy>();
    for( byte i = 0; i < D20Rules.Save.NAMES.length; i++ )
    {
      savesDomain.put( "v" + D20Rules.Save.NAMES[i], new WotCImportLogic.SaveLogic( i ) );
    }
    strategiesByDomain.put( "saves", savesDomain );

    return strategiesByDomain;
  }
  */
  public java.util.List<CreatureTemplate> parseCreatures( CreatureImportServices svc, ImportMessageLog log, Document doc ) throws ImportCreatureException
  {
    ArrayList<CreatureTemplate> creatures = new ArrayList<CreatureTemplate>();
    Element root = doc.getDocumentElement();

    checkSignature( root );

    for( Node child = root.getFirstChild(); child != null; child = child.getNextSibling() )
    {

      if( child.getNodeType() == child.ELEMENT_NODE && "CharacterSheet".equals( child.getNodeName() ) )
      {
        creatures.add( parseCreature( svc, "WotC 4th Edition", child ) );
      }
    }

    return creatures;
  }
  
  private static CreatureTemplate parseCreature( CreatureImportServices svc, String gameSystem, Node WotCNode ) throws ImportCreatureException
  {
    CreatureTemplate ctr = new CreatureTemplate();

    ctr.setGameSystem( "4e" );

    ctr.setModuleName( "WotC 4th Edition" );
    ctr.setNotes( "Imported from WotC Character Generator on " + DateFormat.getDateInstance().format( new Date() ) + "\n" );

    parse( svc, ctr, null, WotCNode );

    // Build the notes tab by combining notes, abilities, and error log
    ctr.buildFullNotes();

    return ctr;
  }   
  
  private static void parse( CreatureImportServices svc, CreatureTemplate ctr, String domain, Node node ) throws ImportCreatureException
  {
//    Map<String, Map<String, CreatureAttributeImportStrategy>> strategies = buildDomainStrategies( ctr.getGameSystem() );

    for( Node child = node.getFirstChild(); child != null; child = child.getNextSibling() )
    {
      if( child.getNodeType() == child.ELEMENT_NODE )
      {
        String nodeName = child.getNodeName();
        
        if( "Details".equals(nodeName))
        {
        	WotCImportLogic.DetailLogic.applyCreatureAttributes( svc, ctr, child );
        }
        else if( "StatBlock".equals( nodeName ) )
        {
          WotCImportLogic.StatBlockLogic.applyCreatureAttributes( svc, ctr, child );
        }
        else if( "LootTally".equals( nodeName ) )
        {
        	WotCImportLogic.GearLogic.applyCreatureAttributes( ctr, child );
        }
        else if( "PowerStats".equals(nodeName))
        {
          WotCImportLogic.PowersLogic.applyCreatureAttributes( ctr, child );
        }
        else if( "RulesElementTally".equals( nodeName ) )
        {
          WotCImportLogic.FeatLogic.applyCreatureAttributes( ctr, child );
        }
        else if( "D20CampaignSetting".equals( nodeName ) )
        {
          ctr.addToNotes("Imported Character had special restrictions.  Unable to import special restrictions"); // Do nothing with this section
        }
        else if( null != nodeName )
        {
          parse( svc, ctr, nodeName, child );
        }
      }
    }
  }
  
  private static CreatureAttributeImportStrategy findStrategy( CreatureTemplate ctr, String domain, Node child, Map<String, Map<String, CreatureAttributeImportStrategy>> strategies )
  {
    Map<String, CreatureAttributeImportStrategy> domainMap = strategies.get( domain );
    if( null == domainMap )
    {
      ctr.addToErrorLog( "Unable to find strategy for domain: " + domain );
      return null;
    }

    Node idNode = child.getAttributes().getNamedItem( "id" );
    String idValue = idNode.getNodeValue();
    if( null != idNode )
      return domainMap.get( idValue );

    ctr.addToErrorLog( "Unable to find strategy for id: " + idValue );
    return null;
  }
  
  private static void checkSignature( Element root ) throws ImportCreatureException
  {
    String sig = root.getAttribute( "game-system" );
    if( null == sig )
      throw new ImportCreatureException( "Unable to find game-system" );
    if( !"D&D4E".equals( sig ) )
      throw new ImportCreatureException( "Not WotC 4th edition character file" );
  }
  
  public List<CreatureTemplate> importCreatures( CreatureImportServices svc, ImportMessageLog log ) throws ImportCreatureException
  {
 /*   if( true )
    {
      D20LF.Dlg.showInfo( svc.accessAnchor(), "Coming soon!" );
      return new ArrayList<CreatureTemplate>();
    }
 */   
    java.util.List<File> files = svc.chooseFiles( this );

    java.util.List<CreatureTemplate> creatures = new XMLToDocumentHelper().convert( svc, log, files, this );

    return creatures;
  }
  
  public FileFilterForExtension getPluginFileFilter()
  {
    return new FileFilterForExtension( "dnd4e", "DDI output" );
  }
}
