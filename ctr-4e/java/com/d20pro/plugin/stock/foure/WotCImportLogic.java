package com.d20pro.plugin.stock.foure;

import java.util.*;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.d20pro.plugin.api.*;
import com.mindgene.common.ObjectLibrary;
import com.mindgene.d20.common.D20Rules;
import com.mindgene.d20.common.creature.*;
import com.mindgene.d20.common.creature.attack.*;
import com.mindgene.d20.common.dice.Dice;
import com.mindgene.d20.common.dice.DiceFormatException;
import com.mindgene.d20.common.game.creatureclass.*;
import com.mindgene.d20.common.game.effect.*;
import com.mindgene.d20.common.game.feat.GenericFeat;
import com.mindgene.d20.common.game.skill.*;
import com.mindgene.d20.common.game.spell.SpellEffectTemplate;
import com.mindgene.d20.common.item.ItemTemplate;

/**
 * Static container for logic to import from WotC 4e character generator.
 * 
 * @author Wesley Lee
 */
public class WotCImportLogic
{
  /**
   * @todo saethi fix this per DE55.
   */

  private WotCImportLogic()
  {
    /** static only */
  }

  private static boolean isOn( NamedNodeMap attr, String name )
  {
    Node item = attr.getNamedItem( name );
    if( null != item )
      return "true".equals( item.getNodeValue() );
    return false;
  }

  static HashMap init()
  {
    HashMap domain = new HashMap(); // domain.put( "", );

    domain.put( "Size", new SizeLogic() );

    // NAMES = { "Natural", "Armor", "Shield", "Deflect", "Enhancement", "Dodge"
    // };
    String[] ID = { "ACNatural", "ACArmor", "ACShield", "ACDeflect", "ACMiscellaneous", "ACDodge" };
    for( byte i = 0; i < ID.length; i++ )
    {
      domain.put( ID[i], new ArmorClassLogic( i ) );
    }

    for( byte i = 0; i < D20Rules.Money.NAMES.length; i++ )
    {
      domain.put( D20Rules.Money.NAMES[i], new MoneyLogic( i ) );
    }

    return domain;
  }

  static class SizeLogic extends SimpleValueStrategy
  {
    @Override
    protected void applyValue( CreatureImportServices svc, CreatureTemplate ctr, String value ) throws ImportCreatureException
    {
      ctr.setSize( D20Rules.Size.getID( value ) );
    }
  }

  static class ArmorClassLogic extends SimpleValueStrategy
  {
    byte _id;

    ArmorClassLogic( byte id )
    {
      _id = id;
    }

    @Override
    protected void applyValue( CreatureImportServices svc, CreatureTemplate ctr, String value )
    {
      value = value.replace( '+', ' ' ).trim(); // strip out plus
      ctr.assignAC( _id, Byte.parseByte( value ) );
    }
  }

  static class MoneyLogic extends SimpleValueStrategy
  {
    private final byte _id;

    MoneyLogic( byte id )
    {
      _id = id;
    }

    @Override
    protected void applyValue( CreatureImportServices svc, CreatureTemplate ctr, String value )
    {
      ctr.setMoney( _id, Integer.parseInt( value ) );
    }
  }

  /**
   * <keyvalue id="aCHA" name="Charisma" value="8" />
   * 
   * @author saethi
   */
  static class AttributeLogic extends SimpleValueStrategy
  {
    private final byte _id;

    AttributeLogic( byte id )
    {
      _id = id;
    }

    @Override
    protected final void applyValue( CreatureImportServices svc, CreatureTemplate ctr, String value ) throws ImportCreatureException
    {
      if( value.indexOf( '/' ) == -1 )
      {
        //TODO change setAbilityScoreBase to AbilityScoreBase
        //TODO added lost method param, clarify is that behavior correct
        ctr.setAbilityScore(_id, DefaultByteValueStrategy.extractByte(value));
      }
      else
      {
        StringTokenizer sToke = new StringTokenizer( value, "/" );
        byte raw = DefaultByteValueStrategy.extractByte( sToke.nextToken() );
        byte mod = DefaultByteValueStrategy.extractByte( sToke.nextToken() );
        //TODO change setAbilityScoreBase to AbilityScoreBase
        ctr.setAbilityScore(_id, mod );
        StringBuilder msg = new StringBuilder( D20Rules.Ability.getFullName( _id ) );
        msg.append( " has been modified by " );
        int dif = mod - raw;
        if( dif > 0 )
          msg.append( '+' );
        msg.append( dif );
        ctr.addToNotes( new String( msg ) );
      }
    }
  }

  /**
   * <keyvalue id="vFort" name="Fortitude Save" value="15" />
   *
   * @author saethi
   */
  static class SaveLogic extends DefaultByteValueStrategy
  {
    SaveLogic( byte id )
    {
      super( id );
    }

    @Override
    protected void applyByte( CreatureTemplate ctr, byte id, byte value )
    {
      ctr.setSave( id, value );
    }
  }

  static class FeatLogic
  {
    public static void applyCreatureAttributes( CreatureTemplate ctr, Node data ) throws ImportCreatureException
    {
      ArrayList feats = new ArrayList();
      for( Node child = data.getFirstChild(); child != null; child = child.getNextSibling() )
      {
        if( child.getNodeType() == child.ELEMENT_NODE )
        {

          NamedNodeMap attr = child.getAttributes();

          // Get a feat
          if( attr.getNamedItem( "type" ).getNodeValue().equalsIgnoreCase( "feat" ) )
          {
            String featName = attr.getNamedItem( "name" ).getNodeValue();
            feats.add( new GenericFeat( featName ) );
          }

          // Extract the class
          if( attr.getNamedItem( "type" ).getNodeValue().equalsIgnoreCase( "class" ) )
          {
            String className = attr.getNamedItem( "name" ).getNodeValue();
            ctr.addToNotes( "Class: " + className );
          }

          // Extract the race
          if( attr.getNamedItem( "type" ).getNodeValue().equalsIgnoreCase( "race" ) )
          {
            String className = attr.getNamedItem( "name" ).getNodeValue();
            ctr.addToNotes( "Race: " + className );
          }
        }
      }
      ctr.getFeats().setFeats( (GenericFeat[])feats.toArray( new GenericFeat[ 0 ] ) );
    }
  }

  static class DetailLogic
  {
    protected static void extractMoney( CreatureTemplate ctr, String value )
    {
      // remove commas
      value = value.replace( ",", "" );

      String[] str = value.split( ";" );

      for( int i = 0; i < str.length; i++ )
      {
        if( str[i].contains( "pp" ) )
        {
          String[] str2 = str[i].split( "pp" );
          ctr.setMoney( D20Rules.Money.PP, Integer.parseInt( str2[0].trim() ) );
        }
        if( str[i].contains( "gp" ) )
        {
          String[] str2 = str[i].split( "gp" );
          ctr.setMoney( D20Rules.Money.GP, Integer.parseInt( str2[0].trim() ) );
        }

        if( str[i].contains( "sp" ) )
        {
          String[] str2 = str[i].split( "sp" );
          ctr.setMoney( D20Rules.Money.SP, Integer.parseInt( str2[0].trim() ) );
        }

        if( str[i].contains( "cp" ) )
        {
          String[] str2 = str[i].split( "cp" );
          ctr.setMoney( D20Rules.Money.CP, Integer.parseInt( str2[0].trim() ) );
        }
      }
    }

    public static void addBaseAbilities( CreatureTemplate ctr )
    {
      // Always have Second Wind, and Daily Item useage charge counters
      SpecialAbility itemDaily = new SpecialAbility();
      SpecialAbility actionPoint = new SpecialAbility();

      itemDaily.setName( "Item Daily Usage" );
      itemDaily.setUsesTotal( (short)1 );
      itemDaily.setUsesRemain( (short)1 );
      itemDaily.setUseMode( SpecialAbility.Uses.CHARGE );

      actionPoint.setName( "Action Point" );
      actionPoint.setUsesTotal( (short)1 );
      actionPoint.setUsesRemain( (short)1 );
      actionPoint.setUseMode( SpecialAbility.Uses.CHARGE );

      try
      {
        ctr.getSpecialAbilities().addAbility( itemDaily );
        ctr.getSpecialAbilities().addAbility( actionPoint );
      }
      catch( Exception e )
      {
        ctr.addToErrorLog( "Error Adding Basic abilities" );
      }

    }

    public static void applyCreatureAttributes( CreatureImportServices svc, CreatureTemplate ctr, Node data ) throws ImportCreatureException
    {
      addBaseAbilities( ctr );

      for( Node child = data.getFirstChild(); child != null; child = child.getNextSibling() )
      {
        if( child.getNodeType() == child.ELEMENT_NODE )
        {
          if( "name".equals( child.getNodeName() ) )
            ctr.setName( child.getTextContent().trim() );

          if( "Alignment".equals( child.getNodeName() ) )
        	ctr.setAlignment(child.getTextContent().trim());

          if( "Experience".equals( child.getNodeName() ) )
              ctr.setExperiencePoints( child.getTextContent().trim() );

          if( "Level".equals( child.getNodeName() ) )
          {
            CreatureClassBinder binder = svc.accessClasses();

            ArrayList<GenericCreatureClass> classes = new ArrayList<GenericCreatureClass>();

            try
            {
              GenericCreatureClass aClass = new GenericCreatureClass( binder.accessClass( "4thEd" ) );
              aClass.setCreature( ctr );
              aClass.setLevel( Byte.parseByte( child.getTextContent().trim() ) );
              classes.add( aClass );
            }
            catch( Exception uve )
            {
              ctr.addToErrorLog( "Unable to import: 4thEd " + child.getTextContent().trim() + " :" + uve.getMessage() );
              ctr.addToErrorLog( "Defaulting to wizard" );

              try
              {
                GenericCreatureClass aClass = new GenericCreatureClass( binder.accessClass( "Wizard" ) );
                aClass.setCreature( ctr );
                aClass.setLevel( Byte.parseByte( child.getTextContent().trim() ) );
                classes.add( aClass );
              }
              catch( CreatureClassNotInstalledException e )
              {
                throw new ImportCreatureException( "Wizard not available", e );
              }

            }
            ctr.getClasses().assignClasses( classes );
          }

          if( "Player".equals( child.getNodeName() ) )
            ctr.addToBackground( "Player: " + child.getTextContent().trim() );

          if( "Height".equals( child.getNodeName() ) )
            ctr.addToBackground( "Height: " + child.getTextContent().trim() );

          if( "Weight".equals( child.getNodeName() ) )
            ctr.addToBackground( "Weight: " + child.getTextContent().trim() );

          if( "Gender".equals( child.getNodeName() ) )
            ctr.addToBackground( "Gender: " + child.getTextContent().trim() );

          if( "Age".equals( child.getNodeName() ) )
            ctr.addToBackground( "Age: " + child.getTextContent().trim() );

          if( "Alignment".equals( child.getNodeName() ) )
            ctr.addToBackground( "Alignment: " + child.getTextContent().trim() );

          if( "Experience".equals( child.getNodeName() ) )
            ctr.addToBackground( "Experience: " + child.getTextContent().trim() );

          if( "CarriedMoney".equals( child.getNodeName() ) )
          {
            // TODO add logic to set money
            extractMoney( ctr, child.getTextContent().trim() );
            ctr.addToBackground( "CarriedMoney: " + child.getTextContent().trim() );
          }

          if( "Portrait".equals( child.getNodeName() ) )
          {
            // TODO add logic to grab the location of pic and attempt to import
            // it into d20Pro
            // <Portrait> file://C:\DND\4th
            // edition\Characters\pics\troll_Slayer.jpg </Portrait>
          }

          if( "Appearance".equals( child.getNodeName() ) )
            ctr.addToBackground( "Apperance: " + child.getTextContent().trim() );

          if( "Notes".equals( child.getNodeName() ) )
            ctr.addToBackground( "Notes: " + child.getTextContent().trim() );
        }
      }

    }
  }

  // Process the WotC StatBlock area
  static class StatBlockLogic
  {
    public static String getFieldAliasName( Node data )
    {
      String fieldName = "";

      for( Node child = data.getFirstChild(); child != null; child = child.getNextSibling() )
      {
        if( child.getNodeType() == child.ELEMENT_NODE )
        {
          NamedNodeMap attr = child.getAttributes();
          return attr.getNamedItem( "name" ).getNodeValue();
        }
      }
      return fieldName;
    }

    public static boolean isAbilityScore( CreatureTemplate ctr, NamedNodeMap attr, String fieldName )
    {
      String fieldValue = attr.getNamedItem( "value" ).getNodeValue();

      if( "Strength".equals( fieldName ) )
      {
        ctr.setAbilityScore(D20Rules.Ability.STR, Byte.parseByte(fieldValue.trim() )  );
        return true;
      }

      if( "Constitution".equals( fieldName ) )
      {
        ctr.setAbilityScore(D20Rules.Ability.CON, Byte.parseByte(fieldValue.trim() )   );
        return true;
      }

      if( "Dexterity".equals( fieldName ) )
      {
        ctr.setAbilityScore(D20Rules.Ability.DEX, Byte.parseByte(fieldValue.trim() )   );
        return true;
      }

      if( "Intelligence".equals( fieldName ) )
      {
        ctr.setAbilityScore(D20Rules.Ability.INT, Byte.parseByte(fieldValue.trim() )   );
        return true;
      }

      if( "Wisdom".equals( fieldName ) )
      {
        ctr.setAbilityScore(D20Rules.Ability.WIS, Byte.parseByte(fieldValue.trim() )   );
        return true;
      }

      if( "Charisma".equals( fieldName ) )
      {
        ctr.setAbilityScore(D20Rules.Ability.CHA, Byte.parseByte(fieldValue.trim() )   );
        return true;
      }

      return false;
    }

    public static void applyACLogic( CreatureTemplate ctr, Node data )
    {
      short armor = 0;
      short shield = 0;
      short enhancement = 0;
      short stat = 0;
      short level = (short)(ctr.getClasses().resolveLevel() / 2);

      // Get the overall value first
      NamedNodeMap mainAttr = data.getAttributes();
      short totalAC = Short.parseShort( mainAttr.getNamedItem( "value" ).getNodeValue() );

      // Get AC components
      for( Node child = data.getFirstChild(); child != null; child = child.getNextSibling() )
      {
        if( child.getNodeType() == child.ELEMENT_NODE )
        {
          if( "statadd".equals( child.getNodeName() ) )
          {

            NamedNodeMap attr = child.getAttributes();
            if( attr.getNamedItem( "type" ) != null )
            {
              String type = attr.getNamedItem( "type" ).getNodeValue();
              String value = attr.getNamedItem( "value" ).getNodeValue();

              if( "armor".equalsIgnoreCase( type ) )
                armor = Short.parseShort( value );

              if( "shield".equalsIgnoreCase( type ) )
                shield = Short.parseShort( value );

              if( "enhancement".equalsIgnoreCase( type ) )
                enhancement = Short.parseShort( value );
            }
          }
        }
      }
      totalAC = totalAC;

      // Set the AC value
      byte[] _ac = new byte[ 6 ];

      for( int i = 0; i < 6; i++ )
        _ac[i] = 0;

      // "Natural", "Armor", "Shield", "Deflect", "Enhancement", "Dodge"
      _ac[0] = (byte)level; // level Value
      _ac[1] = (byte)(armor + enhancement); // Armor value
      _ac[2] = (byte)shield; // Shield Value
      _ac[3] = (byte)(totalAC - 10 - armor - shield - enhancement - level); // ability
                                                                            // Value

      ctr.setAC( _ac );
      ctr.setMaxDexBonus( (short)0 );
    }

    // Create a healing effect based on 1/4 of creatures total hitpoints
    // Then add an ability called Healing Surge to the creature
    public static void applyHealingSurgerLogic( CreatureTemplate ctr, String times )
    {
      SpecialAbility ability = new SpecialAbility();
      EffectModifiers effectModifiers = new EffectModifiers();
      ArrayList hpDelta = new ArrayList();
      EffectDeltaHPFixed surgeValue = new EffectDeltaHPFixed();
      CreatureAttackQuality_Healing type = new CreatureAttackQuality_Healing();

      surgeValue.setModifier( ctr.getHPMax() / 4 );
      surgeValue.setType( type );
      hpDelta.add( surgeValue );
      effectModifiers.assignDeltaHP( hpDelta );

      SpellEffectTemplate effect = SpellEffectTemplate.buildDefault();

      effect.setName( "Healing Surge" );
      effect.setEffectModifiers( effectModifiers );

      ability.setName( "Healing Surge" );
      ability.setUsesTotal( Short.parseShort( times.trim() ) );
      ability.setUsesRemain( Short.parseShort( times.trim() ) );
      ability.setUseMode( SpecialAbility.Uses.PER_DAY );
      ability.setEffect( effect );

      try
      {
        ctr.getSpecialAbilities().addAbility( ability );
      }
      catch( Exception e )
      {
        ctr.addToErrorLog( "Error Adding Healing Surges" );
      }
    }

    public static void addSecondwind( CreatureTemplate ctr )
    {
      SpecialAbility ability = new SpecialAbility();
      EffectModifiers effectModifiers = new EffectModifiers();
      SpellEffectTemplate effect = SpellEffectTemplate.buildDefault();

      EffectModifierAC ACModifiers = new EffectModifierAC();
      EffectScoreModifier[] customModifier = new EffectScoreModifier[ 3 ];

      for( int i = 0; i < 3; i++ )
      {
        customModifier[i] = new EffectScoreModifier();
        customModifier[i].setModifier( 2 );
      }
      ACModifiers.getUnnamedModifier().setModifier( 2 );
      ACModifiers.setCustomModifier( customModifier );

      effectModifiers.setACModifiers( ACModifiers );

      effect.setName( "Second Wind" );
      effect.setEffectModifiers( effectModifiers );
      effect.setDurationMode( D20Rules.Duration.ROUND );
      effect.setDuration( 1 );

      ability.setName( "Second Wind" );
      ability.setUsesTotal( (short)1 );
      ability.setUsesRemain( (short)1 );
      ability.setUseMode( SpecialAbility.Uses.PER_INIT );
      ability.setEffect( effect );

      try
      {
        ctr.getSpecialAbilities().addAbility( ability );
      }
      catch( Exception e )
      {
        ctr.addToErrorLog( "Error Adding Second Wind" );
      }
    }

    // return -10 if skill not found
    public static short getModFromSkill( CreatureTemplate ctr, String skillName )
    {

      // Charisma based skills
      if( "Bluff".equals( skillName ) || "Diplomacy".equals( skillName ) || "Intimidate".equals( skillName ) || "Streetwise".equals( skillName ) )
      {
        return (short)((ctr.getAbilityScore(D20Rules.Ability.CHA ) - 10) / 2);
      }

      // Constitution based skills
      if( "Endurance".equals( skillName ) )
      {
        return (short)((ctr.getAbilityScore(D20Rules.Ability.CON ) - 10) / 2);
      }

      // Dex based skills
      if( "Acrobatics".equals( skillName ) || "Stealth".equals( skillName ) || "Thievery".equals( skillName ) )
      {
        return (short)((ctr.getAbilityScore(D20Rules.Ability.DEX ) - 10) / 2);
      }

      // Int based skills
      if( "Arcana".equals( skillName ) || "History".equals( skillName ) || "Religion".equals( skillName ) )
      {
        return (short)((ctr.getAbilityScore(D20Rules.Ability.INT ) - 10) / 2);
      }

      // STR based skills
      if( "Athletics".equals( skillName ) )
      {
        return (short)((ctr.getAbilityScore(D20Rules.Ability.STR ) - 10) / 2);
      }
      // WIS based skills
      if( "Dungeoneering".equals( skillName ) || "Heal".equals( skillName ) || "Insight".equals( skillName ) || "Nature".equals( skillName )
          || "Perception".equals( skillName ) )
      {
        return (short)((ctr.getAbilityScore(D20Rules.Ability.WIS ) - 10) / 2);
      }

      return -10; // Skill not found in list
    }

    public static boolean isSkill( CreatureTemplate ctr, NamedNodeMap attr, String skillName, ArrayList skills, SkillBinder binder )
    {
      String skillStrRanks = attr.getNamedItem( "value" ).getNodeValue();
      short skillMod = getModFromSkill( ctr, skillName );

      if( skillMod != -10 )
      {
        short skillRanks = (short)(Short.parseShort( skillStrRanks ) - skillMod);
        short level = (short)(ctr.getClasses().resolveLevel() / 2);
        short misc = (short)(skillRanks - level);

        GenericSkillTemplate skillTemplate = binder.accessSkill( skillName );
        if( null != skillTemplate )
        {
          skills.add( new GenericSkill( skillTemplate, level, misc ) );
        }
        else
        {
          ctr.addToErrorLog( "Unknown skill: " + skillName + " Modifer: " + skillRanks );
        }
        return true;
      }

      return false;
    }

    public static void applyCreatureAttributes( CreatureImportServices svc, CreatureTemplate ctr, Node data ) throws ImportCreatureException
    {
      // Objects to hold skills found
      SkillBinder binder = svc.accessSkills();

      ArrayList<GenericSkill> skills = new ArrayList<GenericSkill>();

      for( Node child = data.getFirstChild(); child != null; child = child.getNextSibling() )
      {

        if( child.getNodeType() == child.ELEMENT_NODE )
        {
          // Get the data from the field
          NamedNodeMap attr = child.getAttributes();
          String fieldName = getFieldAliasName( child );
          String fieldValue = attr.getNamedItem( "value" ).getNodeValue();

          // Check to see if it is an ability score if so the continue
          if( isAbilityScore( ctr, attr, fieldName ) )
            continue;

          // Process Armor Class
          if( "AC".equals( fieldName ) )
          {
            applyACLogic( ctr, child );
            continue;
          }

          // Process Hit Points
          if( "Hit Points".equals( fieldName ) )
          {
            ctr.setHPMax( Short.parseShort( fieldValue.trim() ) );
            ctr.setHP( Short.parseShort( fieldValue.trim() ) );
            continue;
          }

          // Process Healing Surges
          if( "Healing Surges".equals( fieldName ) )
          {
            applyHealingSurgerLogic( ctr, fieldValue.trim() );
            addSecondwind( ctr );
            continue;
          }

          // Custom Defense Scores
          if( "Fortitude Defense".equals( fieldName ) )
          {
            ctr.getCustomDefense()[0] = Short.parseShort( fieldValue.trim() );
            continue;
          }

          if( "Reflex Defense".equals( fieldName ) )
          {
            ctr.getCustomDefense()[1] = Short.parseShort( fieldValue.trim() );
            continue;
          }

          if( "Will Defense".equals( fieldName ) )
          {
            ctr.getCustomDefense()[2] = Short.parseShort( fieldValue.trim() );
            continue;
          }

          // Apply Initiative Override since 4ed creatures use totally different
          // init modifier
          if( "Initiative".equals( fieldName ) )
          {
            ctr.setInitOverride( Short.parseShort( fieldValue.trim() ) );
            continue;
          }

          // Add Passive Perception and Insight to Notes
          if( "Passive Perception".equals( fieldName ) )
          {
            ctr.addToNotes( "Passive Perception: " + fieldValue.trim() );
            continue;
          }

          if( "Passive Insight".equals( fieldName ) )
          {
            ctr.addToNotes( "Passive Insight: " + fieldValue.trim() );
            continue;
          }

          // Set speed in squares not in feet
          if( "Speed".equals( fieldName ) )
          {
            String value = fieldValue.trim();
            try
            {
              ctr.accessSpeeds().assignLegacySpeed( Integer.parseInt( value ) );
            }
            catch( NumberFormatException nfe )
            {
              ctr.addToErrorLog( "Failed to parse speed: " + value );
              ctr.accessSpeeds().assignLegacySpeed( CreatureSpeeds.DEFAULT_SPEED );
            }
            continue;
          }

          // Check to see if it is a skill if so the continue
          if( isSkill( ctr, attr, fieldName, skills, binder ) )
            continue;
        }
      }

      // Add any skills found into the creature
      ctr.getSkills().setSkills( skills.toArray( new GenericSkill[ skills.size() ] ) );
    }
  }

  static class PowersLogic
  {
    public static void addAbility( CreatureTemplate ctr, String name, String usage )
    {
      byte frequency = 3; // Default to a charge item

      if( "At-Will".equalsIgnoreCase( usage ) )
        frequency = 0;
      else if( "Encounter".equalsIgnoreCase( usage ) )
        frequency = 2;
      else if( "Daily".equalsIgnoreCase( usage ) )
        frequency = 1;

      SpecialAbility ability = new SpecialAbility();
      ability.setName( name );
      ability.setUsesTotal( (short)1 );
      ability.setUsesRemain( (short)1 );
      ability.setUseMode( frequency );

      try
      {
        ctr.getSpecialAbilities().addAbility( ability );
        // Remove default ability if one was added
        ctr.getSpecialAbilities().deleteAbility( "spontaneous ability" );
      }
      catch( Exception e )
      {
        ctr.addToErrorLog( "Error Special Ability: " + name );
      }
    }

    public static short setAttackStat( CreatureTemplate ctr, CreatureAttack attack, String attackStat )
    {
      if( attackStat.toLowerCase().contains( "str" ) )
      {
        attack.setAbilityToHit( D20Rules.Ability.STR );
        attack.setAbilityToDamage( D20Rules.Ability.STR );
        return (short)((ctr.getAbilityScore(D20Rules.Ability.STR ) - 10) / 2);
      }

      if( attackStat.toLowerCase().contains( "dex" ) )
      {
        attack.setAbilityToHit( D20Rules.Ability.DEX );
        attack.setAbilityToDamage( D20Rules.Ability.DEX );
        return (short)((ctr.getAbilityScore(D20Rules.Ability.DEX ) - 10) / 2);
      }

      if( attackStat.toLowerCase().contains( "con" ) )
      {
        attack.setAbilityToHit( D20Rules.Ability.CON );
        attack.setAbilityToDamage( D20Rules.Ability.CON );
        return (short)((ctr.getAbilityScore(D20Rules.Ability.CON ) - 10) / 2);
      }

      if( attackStat.toLowerCase().contains( "int" ) )
      {
        attack.setAbilityToHit( D20Rules.Ability.INT );
        attack.setAbilityToDamage( D20Rules.Ability.INT );
        return (short)((ctr.getAbilityScore(D20Rules.Ability.INT ) - 10) / 2);
      }

      if( attackStat.toLowerCase().contains( "wis" ) )
      {
        attack.setAbilityToHit( D20Rules.Ability.WIS );
        attack.setAbilityToDamage( D20Rules.Ability.WIS );
        return (short)((ctr.getAbilityScore(D20Rules.Ability.WIS ) - 10) / 2);
      }

      if( attackStat.toLowerCase().contains( "cha" ) )
      {
        attack.setAbilityToHit( D20Rules.Ability.CHA );
        attack.setAbilityToDamage( D20Rules.Ability.CHA );
        return (short)((ctr.getAbilityScore(D20Rules.Ability.CHA ) - 10) / 2);
      }
      return 0;
    }

    public static void setAttackVS( CreatureAttack attack, String attackDefense )
    {
      if( attackDefense.toLowerCase().contains( "ac" ) )
      {
        attack.setDefense( (byte)0 );
      }

      if( attackDefense.toLowerCase().contains( "reflex" ) )
      {
        attack.setDefense( (byte)4 );
      }

      if( attackDefense.toLowerCase().contains( "fortitude" ) )
      {
        attack.setDefense( (byte)3 );
      }

      if( attackDefense.toLowerCase().contains( "will" ) )
      {
        attack.setDefense( (byte)5 );
      }

    }

    public static void discoverDamageType( String attackName, CreatureAttackDamage damage )
    {

      if( attackName.toLowerCase().contains( "rod" ) || attackName.toLowerCase().contains( "symbol" ) || attackName.toLowerCase().contains( "wand" )
          || attackName.toLowerCase().contains( "totem" ) || attackName.toLowerCase().contains( "orb" ) || attackName.toLowerCase().contains( "tome" )
          || attackName.equalsIgnoreCase( "staff" ) )
      {
        // If there are no types default to magic else add nothing
        // If there is a type then do not add Slash, Bash, or Pierce
        if( damage.getQualities().isEmpty() )
          damage.addQuality( new CreatureAttackQuality_Magic() );
      }
      else if( attackName.toLowerCase().contains( "sword" ) || attackName.toLowerCase().contains( "axe" ) || attackName.toLowerCase().contains( "blade" )
          || attackName.toLowerCase().contains( "scimitar" ) || attackName.toLowerCase().contains( "kama" ) || attackName.toLowerCase().contains( "scythe" )
          || attackName.toLowerCase().contains( "halberd" ) || attackName.toLowerCase().contains( "falchion" ) || attackName.toLowerCase().contains( "glaive" ) )
        damage.addQuality( new CreatureAttackQuality_Slash() );
      else if( attackName.toLowerCase().contains( "bow" ) || attackName.toLowerCase().contains( "arrow" ) || attackName.toLowerCase().contains( "bolt" )
          || attackName.toLowerCase().contains( "spear" ) || attackName.toLowerCase().contains( "dagger" ) || attackName.toLowerCase().contains( "pick" )
          || attackName.toLowerCase().contains( "dart" ) || attackName.toLowerCase().contains( "javelin" ) )
        damage.addQuality( new CreatureAttackQuality_Pierce() );
      else
        damage.addQuality( new CreatureAttackQuality_Bash() );
    }

    public static void setDamageType( CreatureAttackDamage damage, String attackDamageType )
    {
      if( attackDamageType.length() > 1 )
      {
        if( attackDamageType.equalsIgnoreCase( "cold" ) )
          damage.addQuality( new CreatureAttackQuality_Cold() );

        if( attackDamageType.equalsIgnoreCase( "fire" ) )
          damage.addQuality( new CreatureAttackQuality_Fire() );

        if( attackDamageType.equalsIgnoreCase( "acid" ) )
          damage.addQuality( new CreatureAttackQuality_Acid() );

        if( attackDamageType.equalsIgnoreCase( "electricity" ) || attackDamageType.equalsIgnoreCase( "lightning" ) )
          damage.addQuality( new CreatureAttackQuality_Electricity() );

        if( attackDamageType.equalsIgnoreCase( "thunder" ) )
          damage.addQuality( new CreatureAttackQuality_Thunder() );

        if( attackDamageType.equalsIgnoreCase( "poison" ) )
          damage.addQuality( new CreatureAttackQuality_Poison() );

        if( attackDamageType.equalsIgnoreCase( "force" ) )
          damage.addQuality( new CreatureAttackQuality_Force() );

        if( attackDamageType.equalsIgnoreCase( "radiant" ) )
          damage.addQuality( new CreatureAttackQuality_Radiant() );

        if( attackDamageType.equalsIgnoreCase( "necrotic" ) )
          damage.addQuality( new CreatureAttackQuality_Necrotic() );

        if( attackDamageType.equalsIgnoreCase( "psychic" ) )
          damage.addQuality( new CreatureAttackQuality_Psychic() );
      }
    }

    public static void addAttackWithWeapon( CreatureTemplate ctr, Node child, String powerName ) throws DiceFormatException
    {

      NamedNodeMap weaponAttr = child.getAttributes();
      String attackName = weaponAttr.getNamedItem( "name" ).getNodeValue().trim();
      ;
      String attackBonus = "";
      String attackDamage = "";
      String attackStat = "";
      String attackDefense = "";
      String attackDamageType = "";

      for( Node weaponChild = child.getFirstChild(); weaponChild != null; weaponChild = weaponChild.getNextSibling() )
      {
        if( weaponChild.getNodeType() == weaponChild.ELEMENT_NODE )
        {
          NamedNodeMap attr = child.getAttributes();

          if( "RulesElement".equalsIgnoreCase( weaponChild.getNodeName() ) )
          {
            attackName = attr.getNamedItem( "name" ).getNodeValue().trim();
          }

          if( "AttackBonus".equalsIgnoreCase( weaponChild.getNodeName() ) )
          {
            attackBonus = weaponChild.getTextContent().trim();
            ;
          }

          if( "Damage".equalsIgnoreCase( weaponChild.getNodeName() ) )
          {
            attackDamage = weaponChild.getTextContent().trim();
            ;
          }

          if( "AttackStat".equalsIgnoreCase( weaponChild.getNodeName() ) )
          {
            attackStat = weaponChild.getTextContent().trim();
            ;
          }

          if( "Defense".equalsIgnoreCase( weaponChild.getNodeName() ) )
          {
            attackDefense = weaponChild.getTextContent().trim();
            ;
          }

          if( "DamageType".equalsIgnoreCase( weaponChild.getNodeName() ) )
          {
            attackDamageType = weaponChild.getTextContent().trim();
            ;
          }
        }
      }

      if( !"Unarmed".equalsIgnoreCase( attackName ) && attackDamage.length() > 2 && attackStat.length() > 3 )
      {

        CreatureAttack attack = new CreatureAttack();
        attack.assumeType( new CreatureAttackType_Melee() );
        short statMod = setAttackStat( ctr, attack, attackStat );

        CreatureAttackDamage damage = new CreatureAttackDamage();

        setDamageType( damage, attackDamageType );
        try
        {
          damage.setDice( new Dice( attackDamage ) );
          damage.getDice().setMod( damage.getDice().getMod() - statMod );
        }
        catch( DiceFormatException e )
        {
          String errorMsg = new String();
          errorMsg = "Error Adding attack for: " + powerName + "\nDamage of: " + attackDamage;
          ctr.addToErrorLog( errorMsg );
          damage.setDice( new Dice( "1d1" ) );
        }

        // For basic attacks list just the weapon name
        if( powerName.contains( "Melee Basic Attack" ) || powerName.contains( "Ranged Basic Attack" ) )
          attack.setName( attackName );
        else
          // For powers just list the power name
          attack.setName( powerName );

        attack.setToHit( (short)(Short.parseShort( attackBonus ) - statMod - (ctr.getClasses().resolveLevel() / 2)) );

        attack.setCritMultiplier( (byte)1 );
        attack.setStyle( new CreatureAttackStyle_1Hand() );
        attack.setAttackCascading( false );
        setAttackVS( attack, attackDefense );

        ArrayList damages = new ArrayList();
        discoverDamageType( attackName, damage );
        damages.add( damage );
        attack.setDamages( damages );

        ctr.getAttacks().add( attack );
      }
    }

    public static void applyCreatureAttributes( CreatureTemplate ctr, Node data ) throws ImportCreatureException
    {
      for( Node child = data.getFirstChild(); child != null; child = child.getNextSibling() )
      {
        if( child.getNodeType() == child.ELEMENT_NODE )
        {
          if( "Power".equalsIgnoreCase( child.getNodeName() ) )
          {
            // Set Power information and set in special ability
            NamedNodeMap attr = child.getAttributes();
            String powerName = attr.getNamedItem( "name" ).getNodeValue();
            String powerUsage = "";

            for( Node powerChild = child.getFirstChild(); powerChild != null; powerChild = powerChild.getNextSibling() )
            {
              if( powerChild.getNodeType() == child.ELEMENT_NODE )
              {
                // Get specific data on Power Usage
                if( "specific".equalsIgnoreCase( powerChild.getNodeName() ) )
                {
                  NamedNodeMap attr2 = powerChild.getAttributes();
                  if( "Power Usage".equalsIgnoreCase( attr2.getNamedItem( "name" ).getNodeValue() ) )
                  {
                    powerUsage = powerChild.getTextContent().trim();
                    // Do not add basic attacks to powers
                    if( !(powerName.contains( "Melee Basic Attack" ) || powerName.contains( "Ranged Basic Attack" )) )
                      addAbility( ctr, powerName, powerUsage );
                  }
                }

                if( "Weapon".equalsIgnoreCase( powerChild.getNodeName() ) )
                {
                  String modPowerName = powerName;
                  if( "encounter".equalsIgnoreCase( powerUsage ) )
                    modPowerName = "*" + modPowerName;
                  if( "daily".equalsIgnoreCase( powerUsage ) )
                    modPowerName = "**" + modPowerName;

                  try
                  {
                    addAttackWithWeapon( ctr, powerChild, modPowerName );
                  }
                  catch( Exception e )
                  {
                    ctr.addToErrorLog( ObjectLibrary.buildCollapsedExceptionMessage( e ) );
                  }
                  // Only add one weapon per power
                  break;
                }
              }
            }
          }
        }
      }
    }
  }

  static class GearLogic
  {
    public static String getName( Node child ) throws ImportCreatureException
    {
      String itemName = "";
      for( Node nameChild = child.getFirstChild(); nameChild != null; nameChild = nameChild.getNextSibling() )
      {
        if( nameChild.getNodeType() == child.ELEMENT_NODE )
        {
          NamedNodeMap attr = nameChild.getAttributes();
          if( itemName.length() > 0 )
            itemName = itemName + " " + attr.getNamedItem( "name" ).getNodeValue();
          else
            itemName = attr.getNamedItem( "name" ).getNodeValue();
        }
      }
      return itemName;
    }

    public static String getType( Node child ) throws ImportCreatureException
    {
      String typeName = "";
      for( Node nameChild = child.getFirstChild(); nameChild != null; nameChild = nameChild.getNextSibling() )
      {
        if( nameChild.getNodeType() == child.ELEMENT_NODE )
        {
          NamedNodeMap attr = nameChild.getAttributes();
          typeName = attr.getNamedItem( "type" ).getNodeValue();
        }
      }
      return typeName;
    }

    public static void applyCreatureAttributes( CreatureTemplate ctr, Node data ) throws ImportCreatureException
    {
      CreatureTemplate_Items items = ctr.getItems();
      for( Node child = data.getFirstChild(); child != null; child = child.getNextSibling() )
      {
        if( child.getNodeType() == child.ELEMENT_NODE )
        {
          NamedNodeMap attr = child.getAttributes();

          // Make sure there is at least 1 of the item if not continue
          if( Integer.parseInt( attr.getNamedItem( "count" ).getNodeValue() ) < 1 )
            continue;

          // check to see if it is a ritual if so add to notes section
          if( "ritual".equalsIgnoreCase( getType( child ) ) )
          {
            ctr.addToNotes( "Ritual: " + getName( child ) );
            continue;
          }
          ItemTemplate item = new ItemTemplate();
          item.assignName( getName( child ) );
          item.assignQuantity(attr.getNamedItem("count").getNodeValue() );

          items.addItem( item );
        }
      }
    }
  }

  static class extraTextLogic
  {

    public static void applyCreatureAttributes( CreatureTemplate ctr, Node data ) throws ImportCreatureException
    {
      NamedNodeMap attr = data.getAttributes();
      String text = data.getTextContent();
      String name = attr.getNamedItem( "name" ).getNodeValue();
      // Make sure there is at least 1 of the item if not continue
      if( name != null && name.contains( "USER_NOTE_" ) )
      {
        name = name.replace( "USER_NOTE_", "" );
        ctr.addToNotes( name + ": " + text );
      }
    }
  }
}
