package com.d20pro.plugin.stock.herolab;

import java.util.*;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.d20pro.plugin.api.CreatureImportServices;
import com.mindgene.d20.common.creature.CreatureTemplate;
import com.mindgene.d20.common.creature.capability.CreatureCapability_SpellCaster;
import com.mindgene.d20.common.game.spell.SpellBinder;
import com.mindgene.d20.common.importer.ImportedSpell;

/**
 * Spell logic for Hero Lab import.
 *
 * @author saethi
 */
public class HeroLabImportSpellLogic
{
  private static String extractName( Node data )
  {
    NamedNodeMap attr = data.getAttributes();
    return attr.getNamedItem( "name" ).getNodeValue();
  }

  private static ImportedSpell extractSpell( CreatureTemplate ctr, Node data )
  {
    try
    {
      NamedNodeMap attr = data.getAttributes();
      String name = attr.getNamedItem( "name" ).getNodeValue();
      int level = Integer.parseInt( attr.getNamedItem( "level" ).getNodeValue() );
      int castsLeft = Integer.parseInt( attr.getNamedItem( "castsleft" ).getNodeValue() );
      return new ImportedSpell( name, level, castsLeft );
    }
    catch( Exception e )
    {
      ctr.addToErrorLog( "Failed to extract spell", e );
      return new ImportedSpell( "", 0, 0 );
    }
  }

  private static Map<String, List<ImportedSpell>> extractDomains( CreatureTemplate ctr, Node data )
  {
    Map<String, List<ImportedSpell>> domains = new LinkedHashMap<String, List<ImportedSpell>>();

    for( Node child = data.getFirstChild(); child != null; child = child.getNextSibling() )
    {
      if( child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equals( "domain" ) )
      {
        String domainName = extractName( child );
        for( Node child2 = child.getFirstChild(); child2 != null; child2 = child2.getNextSibling() )
        {
          if( child2.getNodeType() == Node.ELEMENT_NODE )
          {
            List<ImportedSpell> spells = domains.get( domainName );
            if( null == spells )
            {
              spells = new ArrayList<ImportedSpell>();
              domains.put( domainName, spells );
            }
            spells.add( extractSpell( ctr, child2 ) );
          }
        }

        // ensure a domain is in the map even if it has no spells
        if( !domains.containsKey( domainName ) )
          domains.put( domainName, new ArrayList<ImportedSpell>() );
      }
    }

    return domains;
  }

  private static ArrayList<ImportedSpell> extractSpells( SpellBinder binder, CreatureTemplate ctr, Node data )
  {
    ArrayList<ImportedSpell> spells = new ArrayList<ImportedSpell>();

    for( Node child = data.getFirstChild(); child != null; child = child.getNextSibling() )
    {
      if( child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equals( "spell" ) )
      {
        ImportedSpell spell = extractSpell( ctr, child );
        String spellName = spell.getName();

        if( binder.hasSpell( spellName ) )
          spells.add( spell );
        else
        {
          ctr.addToNotes("spell not found: " + spellName);	
        }
      }
    }
    return spells;
  }

  private static ImportedSpell[] asArray( java.util.List<ImportedSpell> spellNames )
  {
    return spellNames.toArray( new ImportedSpell[ spellNames.size() ] );
  }

  private abstract static class SpellImportStrategy
  {
    protected abstract void execute( CreatureImportServices svc, CreatureCapability_SpellCaster casting, ImportedSpell[] spells );

    protected void executeAdditional( CreatureImportServices svc, CreatureCapability_SpellCaster casting, CreatureTemplate ctr, Node data )
    {
      /** stub */
    }
  }

  private static class SpellsKnown extends SpellImportStrategy
  {
    @Override
    protected void execute( CreatureImportServices svc, CreatureCapability_SpellCaster casting, ImportedSpell[] spells )
    {
      casting.importSpellsKnown( spells );
    }
  }

  private static class SpellsMemorized extends SpellImportStrategy
  {
    @Override
    protected void execute( CreatureImportServices svc, CreatureCapability_SpellCaster casting, ImportedSpell[] spells )
    {
      casting.importSpellsMemorized( spells );
    }

    @Override
    protected void executeAdditional( CreatureImportServices svc, CreatureCapability_SpellCaster casting, CreatureTemplate ctr, Node data )
    {
      casting.importSpellsDomain( extractDomains( ctr, data ) );
    }
  }

  /**
   * Iterates over all classes and calls the strategy on each.
   *
   * @param strategy SpellImportStrategy
   */
  private static void applySpellImportStrategy( CreatureImportServices svc, SpellImportStrategy strategy, CreatureTemplate ctr, Node data )
  {
    SpellBinder binder = svc.accessSpells();
    
    for( Node child = data.getFirstChild(); child != null; child = child.getNextSibling() )
    {
      if( child.getNodeType() == Node.ELEMENT_NODE )
      {
        String spellClass = determineSpellClass( child );
        CreatureCapability_SpellCaster casting = ctr.extractSpellCasting( spellClass );

        if( null == casting )
        {
          ctr.addToNotes("spellClass not found: " + spellClass);
          return;
        }

        try
        {
          strategy.execute( svc, casting, asArray( extractSpells( binder, ctr, child ) ) );
          strategy.executeAdditional( svc, casting, ctr, child );
        }
        catch( Exception e )
        {
          ctr.addToErrorLog( "Failed to execute strategy for: " + spellClass, e );
        }
      }
    }
  }

  public static void applySpellsKnown( CreatureImportServices svc, CreatureTemplate ctr, Node data )
  {
    applySpellImportStrategy( svc, new SpellsKnown(), ctr, data );
  }

  public static void applySpellsMemorized( CreatureImportServices svc, CreatureTemplate ctr, Node data )
  {
    applySpellImportStrategy( svc, new SpellsMemorized(), ctr, data );
  }

  private static String determineSpellClass( Node data )
  {
    String spellClass = data.getAttributes().getNamedItem( "class" ).getNodeValue();
    spellClass = spellClass.toLowerCase();
    return spellClass;
  }
}
