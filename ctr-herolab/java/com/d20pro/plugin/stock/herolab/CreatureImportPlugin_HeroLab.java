package com.d20pro.plugin.stock.herolab;

import java.io.File;
import java.text.DateFormat;
import java.util.*;

import org.w3c.dom.*;

import com.d20pro.plugin.api.*;
import com.mindgene.common.util.FileFilterForExtension;
import com.mindgene.d20.common.Rules;
import com.mindgene.d20.common.creature.CreatureTemplate;

/**
 * Import Hero Lab XML into Creatures.
 * 
 * @author saethi
 */
public class CreatureImportPlugin_HeroLab implements ImportCreaturePlugin, XMLToDocumentHelperStrategy
{
  private static String _gameSystem;
  public CreatureImportPlugin_HeroLab()
  {
//    _gameSystem = "3.5";
//    Rules.getInstance().setSystem(Rul);
  }

  public String getPluginName()
  {
    return "Hero Lab";
  }

  public static Map<String, Map<String, CreatureAttributeImportStrategy>> buildDomainStrategies( String gameSystem )
  {
    Map<String, Map<String, CreatureAttributeImportStrategy>> strategiesByDomain = new HashMap<String, Map<String, CreatureAttributeImportStrategy>>();

    strategiesByDomain.put( null, HeroLabImportLogic.buildDefaultStrategies() );

    Map<String, CreatureAttributeImportStrategy> attributesDomain = new HashMap<String, CreatureAttributeImportStrategy>();
    
    String[] NAMES = new String[]{};
    try
    {
      NAMES = (String[]) Rules.getInstance().getFieldValue("Rules.Ability.NAMES");
    }
    catch (Exception e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    for( byte i = 0; i < NAMES.length; i++ )
    {
      if( gameSystem.equalsIgnoreCase( "4e" ) )
        attributesDomain.put( "attr" + NAMES[i].substring( 0, 1 ) + NAMES[i].substring( 1, 3 ).toLowerCase(),
            new HeroLabImportLogic.AttributeLogic( i ) );
      else
        attributesDomain.put( "a" + NAMES[i], new HeroLabImportLogic.AttributeLogic( i ) );
    }
    strategiesByDomain.put( "attributes", attributesDomain );

    NAMES = new String[]{};
    try
    {
      NAMES = (String[]) Rules.getInstance().getFieldValue("Rules.Save.NAMES");
    }
    catch (Exception e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    HashMap<String, CreatureAttributeImportStrategy> savesDomain = new HashMap<String, CreatureAttributeImportStrategy>();
    for( byte i = 0; i < NAMES.length; i++ )
    {
      if( gameSystem.equalsIgnoreCase( "pathfinder" ) )
        savesDomain.put( "sv" + NAMES[i], new HeroLabImportLogic.SaveLogic( i ) );
      else
        savesDomain.put( "v" + NAMES[i], new HeroLabImportLogic.SaveLogic( i ) );
    }
    strategiesByDomain.put( "saves", savesDomain );

    return strategiesByDomain;
  }

  public java.util.List<CreatureTemplate> parseCreatures( CreatureImportServices svc, ImportMessageLog log, Document doc ) throws ImportCreatureException
  {
    ArrayList<CreatureTemplate> creatures = new ArrayList<CreatureTemplate>();
    Element root = doc.getDocumentElement();

    checkSignature( root );

    for( Node child = root.getFirstChild(); child != null; child = child.getNextSibling() )
    {
      determineGameSystem( child );
      if( child.getNodeType() == Node.ELEMENT_NODE && "hero".equals( child.getNodeName() ) )
      {
        creatures.add( parseCreature( svc, _gameSystem.toString(), child ) );
      }
    }

    return creatures;
  }

  private static String determineGameSystem( Node child )
  {
    if( child.getNodeType() == Node.ELEMENT_NODE && "importer".equals( child.getNodeName() ) )
    {
      NamedNodeMap attr = child.getAttributes();

      Node line = attr.getNamedItem( "game" );

      if( line != null )
      {
        _gameSystem = line.getNodeValue();
        //return gameSystem;
      }
    }
    return "3.5";
  }

  private static CreatureTemplate parseCreature( CreatureImportServices svc, String gameSystem, Node heroNode ) throws ImportCreatureException
  {
    CreatureTemplate ctr = new CreatureTemplate();

    ctr.setGameSystem( gameSystem );

    NamedNodeMap attr = heroNode.getAttributes();
    Node name = attr.getNamedItem( "heroname" );
    if( null == name )
      throw new ImportCreatureException( "missing heroname" );
    ctr.setName( name.getNodeValue() );
    ctr.setModuleName( "Hero Lab" );
    ctr.setNotes( "Imported from Hero Lab(" + gameSystem + ") on " + DateFormat.getDateInstance().format( new Date() ) + "\n" );

    parse( svc, ctr, null, heroNode );

    // Build the notes tab by combining notes, abilities, and error log
    ctr.buildFullNotes();

    return ctr;
  }

  private static void parse( CreatureImportServices svc, CreatureTemplate ctr, String domain, Node node ) throws ImportCreatureException
  {
    Map<String, Map<String, CreatureAttributeImportStrategy>> strategies = buildDomainStrategies( ctr.getGameSystem() );

    for( Node child = node.getFirstChild(); child != null; child = child.getNextSibling() )
    {
      if( child.getNodeType() == Node.ELEMENT_NODE )
      {
        String nodeName = child.getNodeName();

        if( "keyvalue".equals( nodeName ) )
        {
          applyStrategy( svc, ctr, domain, child, strategies );
        }
        else if( "attacks".equals( nodeName ) )
        {
          HeroLabImportLogic.AttackLogic.applyCreatureAttributes( ctr, child );
        }
        else if( "feats".equals( nodeName ) )
        {
          HeroLabImportLogic.FeatLogic.applyCreatureAttributes( ctr, child );
        }
        else if( "skills".equals( nodeName ) )
        {
          HeroLabImportLogic.SkillLogic.applyCreatureAttributes( svc, ctr, child, _gameSystem );
        }
        else if( "gear".equals( nodeName ) )
        {
          HeroLabImportLogic.GearLogic.applyCreatureAttributes( ctr, child );
        }
        else if( "specialabilities".equals( nodeName ) )
        {
          HeroLabImportLogic.SpecialAbilityLogic.applyCreatureAttributes( ctr, child );
        }
        else if( "background".equals( nodeName ) )
        {
          HeroLabImportLogic.BackgroundLogic.applyCreatureAttributes( ctr, child );
        }
        else if( "spellsknown".equals( nodeName ) )
        {
          HeroLabImportSpellLogic.applySpellsKnown( svc, ctr, child );
        }
        else if( "spellsmemorized".equals( nodeName ) )
        {
          HeroLabImportSpellLogic.applySpellsMemorized( svc, ctr, child );
        }
        else if( "damagereduction".equals( nodeName ) )
        {
          HeroLabImportLogic.DamageReductionLogic.applyCreatureAttributes( ctr, child );
        }
        else if( "elementalresistances".equals( nodeName ) )
        {
          HeroLabImportLogic.ElementalResistancesLogic.applyCreatureAttributes( ctr, child );
        }
        else if( "defenses".equals( nodeName ) ) // 4e defense stats
        {
          HeroLabImportLogic.Defense4eLogic.applyCreatureAttributes( ctr, child );
        }
        else if( "powers".equals( nodeName ) ) // 4e powers
        {
          HeroLabImportLogic.Power4eLogic.applyCreatureAttributes( ctr, child );
        }
        else if( "userimages".equals( nodeName ) )
        {
          HeroLabImportLogic.UserImagesLogic.applyCreatureAttributes( ctr, child, svc.accessImageService() );
        }
        else if( null != nodeName )
        {
          parse( svc, ctr, nodeName, child );
        }
      }
    }
  }

  private static void applyStrategy( CreatureImportServices svc, CreatureTemplate ctr, String domain, Node node, Map<String, Map<String, CreatureAttributeImportStrategy>> strategies )
      throws ImportCreatureException
  {
    CreatureAttributeImportStrategy strategy = findStrategy( ctr, domain, node, strategies );
    if( null != strategy )
    {
      try
      {
        strategy.applyCreatureAttributes( svc, ctr, node );
      }
      catch( ImportCreatureException ice )
      {
        ctr.addToErrorLog( "Unable to apply strategy: " + ice.getMessage() );
      }
      catch( Exception e )
      {
        ctr.addToErrorLog( "Unable to apply strategy: " + e.getMessage() );
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
    String sig = root.getAttribute( "signature" );
    if( null == sig )
      throw new ImportCreatureException( "Signature not found" );
    if( !"d20Pro Import File".equals( sig ) )
      throw new ImportCreatureException( "Bad signature" );
  }

  public java.util.List<CreatureTemplate> importCreatures( CreatureImportServices svc, ImportMessageLog log ) throws ImportCreatureException
  {
    java.util.List<File> files = svc.chooseFiles( this );

    java.util.List<CreatureTemplate> creatures = new XMLToDocumentHelper().convert( svc, log, files, this );

    return creatures;
  }
  
  public FileFilterForExtension getPluginFileFilter()
  {
    return new FileFilterForExtension( "hld20pro", "Hero Lab output" );
  }
}
