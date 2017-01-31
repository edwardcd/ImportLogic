package com.d20pro.plugin.stock.herolab;

import java.awt.*;
import java.util.*;

import javax.swing.*;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.d20pro.plugin.api.*;
import com.mindgene.common.FileLibrary;
import com.mindgene.common.ObjectLibrary;
import com.mindgene.d20.common.D20Rules;
import com.mindgene.d20.common.creature.*;
import com.mindgene.d20.common.creature.attack.*;
import com.mindgene.d20.common.dice.Dice;
import com.mindgene.d20.common.dice.DiceFormatException;
import com.mindgene.d20.common.game.creatureclass.*;
import com.mindgene.d20.common.game.effect.*;
import com.mindgene.d20.common.game.feat.Feat_InitModifier;
import com.mindgene.d20.common.game.feat.GenericFeat;
import com.mindgene.d20.common.game.skill.*;
import com.mindgene.d20.common.game.spell.SpellEffectTemplate;
import com.mindgene.d20.common.item.ItemTemplate;
import com.mindgene.d20.common.util.D20ImageUtil;
import com.mindgene.d20.common.util.ImageProvider;
import com.mindgene.d20.dm.portable.ImageImportUtil;
import com.sengent.common.logging.LoggingManager;

/**
 * Static container for logic to import from Hero Lab XML.
 * 
 * @author saethi
 */
public class HeroLabImportLogic
{ 
  static short _numberOfSurges = 0; // for 4e importer

  private HeroLabImportLogic()
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

  public static Map<String, CreatureAttributeImportStrategy> buildDefaultStrategies()
  {
    Map<String, CreatureAttributeImportStrategy> domain = new HashMap<String, CreatureAttributeImportStrategy>(); // domain.put(
                                                                                                                  // "",
    domain.put( "Size", new SizeLogic() );
    domain.put( "Space", new SpaceLogic() );
    domain.put( "Reach", new ReachLogic() );
    domain.put( "Speed", new SpeedLogic() );
    domain.put( "Race", new TypeLogic() );
    domain.put( "Class", new ClassLogic() );
    domain.put( "CurrentHP", new HPLogic() );
    domain.put( "MaxHP", new HPMaxLogic() );
    domain.put( "CMDTotal", new CMDTotalLogic() );
    domain.put( "CMDFlat", new CMDFlatLogic() );
    domain.put( "Surges", new SurgeLogic() );
    domain.put( "SurgeValue", new SurgeValueLogic() );
    domain.put("Alignment", new AlignmentLogic());
    domain.put("Experience", new ExperienceLogic());

    String[] NAMES = { "Natural", "Armor", "Shield", "Deflect", "Enhancement", "Dodge" };
    String[] ID = { "ACNatural", "ACArmor", "ACShield", "ACDeflect", "ACMisc", "ACDodge" };
    for( byte i = 0; i < ID.length; i++ )
    {
      domain.put( ID[i], new ArmorClassLogic( i ) );
    }
    
    for( byte i = 0; i < D20Rules.Money.NAMES.length; i++ )
    {
      domain.put( NAMES[i], new MoneyLogic( i ) );
    }

    return domain;
  }

  static class AlignmentLogic extends SimpleValueStrategy
  {
    @Override
    protected void applyValue( CreatureImportServices svc, CreatureTemplate ctr, String value )
    {
      ctr.setAlignment( value );
    }
  }
  
  static class ExperienceLogic extends SimpleValueStrategy
  {
    @Override
    protected void applyValue( CreatureImportServices svc, CreatureTemplate ctr, String value )
    {
      ctr.setExperiencePoints( value );
    }
  }
  
  static class SurgeLogic extends SimpleValueStrategy
  {
    @Override
    protected void applyValue( CreatureImportServices svc, CreatureTemplate ctr, String value )
    {
      _numberOfSurges = Short.parseShort( value );
    }
  }

  static class SurgeValueLogic extends SimpleValueStrategy
  {
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
        ctr.addToErrorLog( "Error Adding Second Wind", e );
      }
    }

    @Override
    protected void applyValue( CreatureImportServices svc, CreatureTemplate ctr, String value )
    {
      addSecondwind( ctr );
      SpecialAbility ability = new SpecialAbility();
      EffectModifiers effectModifiers = new EffectModifiers();
      ArrayList hpDelta = new ArrayList();
      EffectDeltaHPFixed surgeValue = new EffectDeltaHPFixed();
      CreatureAttackQuality_Healing type = new CreatureAttackQuality_Healing();

      surgeValue.setModifier( Integer.parseInt( value ) );
      surgeValue.setType( type );
      hpDelta.add( surgeValue );
      effectModifiers.assignDeltaHP( hpDelta );

      SpellEffectTemplate effect = SpellEffectTemplate.buildDefault();

      effect.setName( "Healing Surge" );
      effect.setEffectModifiers( effectModifiers );

      ability.setName( "Healing Surge" );
      ability.setUsesTotal( _numberOfSurges );
      ability.setUsesRemain( _numberOfSurges );
      ability.setUseMode( SpecialAbility.Uses.PER_DAY );
      ability.setEffect( effect );

      try
      {
        ctr.getSpecialAbilities().addAbility( ability );
      }
      catch( Exception e )
      {
        ctr.addToErrorLog( "Error Adding Healing Surges", e );
      }
    }
  }

  static class SizeLogic extends SimpleValueStrategy
  {
    @Override
    protected void applyValue( CreatureImportServices svc, CreatureTemplate ctr, String value )
    {
      ctr.setSize( D20Rules.Size.getID( value ) );
    }
  }

  static class SpeedLogic extends SimpleValueStrategy
  {
    @Override
    protected void applyValue( CreatureImportServices svc, CreatureTemplate ctr, String value )
    {
      ctr.addToNotes( "Speed: " + value );
      if( value.indexOf( "/" ) != -1 )
        value = value.substring( 0, value.indexOf( "/" ) );
      value = value.trim();
      try
      {
        ctr.accessSpeeds().assignLegacySpeed( CreatureSpeeds.feetToSquares( Integer.parseInt( value ) ) );
      }
      catch( NumberFormatException nfe )
      {
        ctr.addToErrorLog( "Failed to parse speed: " + value );
        ctr.accessSpeeds().assignLegacySpeed( 30 );
      }
    }
  }

  static class SpaceLogic extends SimpleValueStrategy
  {
    @Override
    protected void applyValue( CreatureImportServices svc, CreatureTemplate ctr, String value )
    {
      int space = Integer.parseInt( value ) / 5;
      ctr.setFace( new Dimension( space, space ) );
    }
  }

  static class ReachLogic extends SimpleValueStrategy
  {
    @Override
    protected void applyValue( CreatureImportServices svc, CreatureTemplate ctr, String value )
    {
      byte reach = (byte)(Byte.parseByte( value ) / 5);
      ctr.setReach( reach );
    }
  }

  static class TypeLogic extends SimpleValueStrategy
  {
    @Override
    protected void applyValue( CreatureImportServices svc, CreatureTemplate ctr, String value ) throws ImportCreatureException
    {
      ctr.setType( value );
    }
  }

  /**
   * <keyvalue id="Class" value="Druid 13, Fighter 3" />
   * 
   * @author saethi
   */
  static class ClassLogic extends SimpleValueStrategy
  {
	private String StripArchType(CreatureTemplate ctr, String value)
	{
	  ctr.addToNotes("Classes: " + value );
	  while(value.contains("("))
	  {
	    int start = value.indexOf('(');
	    int end = value.indexOf(')');
	    String archType = value.substring(start, end + 2);
	    
	    value = value.replace(archType, "");
	  }
	  return value;
	}
	
	private String stripDiety(CreatureTemplate ctr, String value)
	{
	  ctr.addToNotes("Classes: " + value );
	  if(value.contains(" of "))
	  {
		value = value.replace(" of ", "(");
	    int start = value.indexOf('(');
	    int end = value.indexOf(' ');
	    String diety = value.substring(start, end);
	    value = value.replace(diety, "");
	  }
	  return value;
	}
    @Override
    protected void applyValue( CreatureImportServices svc, CreatureTemplate ctr, String value ) throws ImportCreatureException
    {
      CreatureClassBinder binder = svc.accessClasses();
      
      value = StripArchType(ctr,value);

      ArrayList<GenericCreatureClass> classes = new ArrayList<GenericCreatureClass>();
      StringTokenizer sToke = new StringTokenizer( value, ",/" );
      while( sToke.hasMoreTokens() )
      {
        String token = sToke.nextToken().trim();
        token = stripDiety(ctr,token);
        int spaceAt = token.lastIndexOf( ' ' );
        if( spaceAt < 0 )
        {
          ctr.addToErrorLog( "Illegal Class and level combo in .XML file: " + classes + " defaulting to Fighter 1" );
          defaultToFighter1( ctr, classes, binder, 1 );

          return;
        }
        String nameOfClass = token.substring( 0, spaceAt ).toLowerCase();
        String levelAsTxt = token.substring( spaceAt + 1 );
        //ensure first letter is capitalized        
        nameOfClass = Character.toUpperCase(nameOfClass.charAt(0)) + nameOfClass.substring(1);
        
        int spaceInName = nameOfClass.lastIndexOf(' ');
        if( spaceInName > 0) 
        {
        	nameOfClass = Character.toUpperCase(nameOfClass.charAt(0)) 
        		+ nameOfClass.substring(1,spaceInName + 1) 
        		+ Character.toUpperCase(nameOfClass.charAt(spaceInName + 1))
        		+ nameOfClass.substring(spaceInName + 2) ;
        	
        }
        byte level = 1;
        try
        {
          level = Byte.parseByte( levelAsTxt );
        }
        catch( NumberFormatException nfe )
        {
          ctr.addToErrorLog( "Illegal level value: " + levelAsTxt );
        }
        
        try
        {
          GenericCreatureClass aClass = new GenericCreatureClass( binder.accessClass( nameOfClass ) );
          aClass.setCreature( ctr );
          aClass.setLevel( level );
          classes.add( aClass );
        }
        catch( CreatureClassNotInstalledException cclnie )
        {
          ctr.addToErrorLog( "Unable to import: " + nameOfClass + " " + level + " :" + cclnie.getMessage() );
          
          defaultToFighter1( ctr, classes, binder, level );
        }
      }

      ctr.getClasses().assignClasses( classes );
    }
    
    private static final String FIGHTER = "Fighter";
    
    private static void defaultToFighter1( CreatureTemplate ctr, ArrayList<GenericCreatureClass> classes, CreatureClassBinder binder, int level )
    {
      try
      {
        ctr.addToErrorLog( "Defaulting to " + FIGHTER );
        GenericCreatureClass fighter = new GenericCreatureClass( binder.accessClass( FIGHTER ) );
        fighter.setLevel( (byte)level );
        fighter.setCreature( ctr );
        classes.add( fighter );
      }
      catch( CreatureClassNotInstalledException cclnie )
      {
        ctr.addToErrorLog( FIGHTER + " class not found, skipping class" );
      }
    }
  }

 
  
  static class HPLogic extends SimpleValueStrategy
  {
    @Override
    protected void applyValue( CreatureImportServices svc, CreatureTemplate ctr, String value )
    {
      ctr.setHP( Short.parseShort( value ) );
    }
  }

  static class CMDTotalLogic extends SimpleValueStrategy
  {
    @Override
    protected void applyValue( CreatureImportServices svc, CreatureTemplate ctr, String value )
    {
      ctr.getCustomDefense()[0] = Short.parseShort( value );
    }
  }

  static class CMDFlatLogic extends SimpleValueStrategy
  {
    @Override
    protected void applyValue( CreatureImportServices svc, CreatureTemplate ctr, String value )
    {
      ctr.getCustomDefense()[1] = Short.parseShort( value );
    }
  }

  static class HPMaxLogic extends SimpleValueStrategy
  {
    @Override
    protected void applyValue( CreatureImportServices svc, CreatureTemplate ctr, String value )
    {
      ctr.setHPMax( Short.parseShort( value ) );
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
      if( value.equals( "-" ) )
      {
    	  ctr.setAbilityScore( _id, CreatureTemplate.NO_ABILITY );
      }
      else if( value.indexOf( '/' ) == -1 )
      {
        ctr.setAbilityScore( _id, DefaultByteValueStrategy.extractByte( value ) );
      }
      else
      {
        StringTokenizer sToke = new StringTokenizer( value, "/" );
        byte raw = DefaultByteValueStrategy.extractByte( sToke.nextToken() );
        byte mod = DefaultByteValueStrategy.extractByte( sToke.nextToken() );
        ctr.setAbilityScore( _id, mod );
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

  /**
   *
   */
  static class AttackLogic
  {
    public static void applyPathfinderDefaultAttack( CreatureTemplate ctr ) throws ImportCreatureException
    {
      CreatureAttack attack = new CreatureAttack();
      attack.setName( "Combat Maneuver" );
      attack.setStyle( new CreatureAttackStyle_1Hand() );
      attack.assumeType( new CreatureAttackType_Melee() );
      attack.setAttackCascading( false );
      attack.setDefense( (byte)3 ); // Set attack vs Combat Maneuver Defense
      attack.setAbilityToDamage(D20Rules.Ability.NONE);
      CreatureAttackDamage damage = new CreatureAttackDamage();
      try
      {
        damage.setDice( new Dice( "1d0" ) );
	  }
	  catch( Exception e )
	  {
	    throw new ImportCreatureException( e.getMessage() );
	  }
      ArrayList damages = new ArrayList();
      damages.add( damage );
      attack.setDamages( damages );

      // Need to undo the normal attack bonus due to size and apply CMB size mod
      // 
      // 3.5 size attack mods { 8, 4, 2, 1, 0, -1, -2, -4, -8, -12 };
      // pathfinder CMB mods Fine -8, Diminutive -4, Tiny -2, Small -1, Medium
      // +0, Large +1, Huge +2, Gargantuan +4, Colossal +8
      // to undo add twice the opposite number
      attack.setToHit( (short)(D20Rules.Size.getMod( ctr.getSize() ) * -2) );

      ctr.getAttacks().add( attack );
    }
    
    public static void discoverDamageIsMagic( String attackName, CreatureAttackDamage damage )
    {
    	if(attackName.contains("+"))
    	  damage.addQuality( new CreatureAttackQuality_Magic() );
    }

    public static void discoverDamageType( String damageType, CreatureAttackDamage damage )
    {
    	if(damageType.contains("S"))
    	  damage.addQuality( new CreatureAttackQuality_Slash() );
    	if(damageType.contains("B"))
      	  damage.addQuality( new CreatureAttackQuality_Bash() );
    	if(damageType.contains("P"))
      	  damage.addQuality( new CreatureAttackQuality_Pierce() );   		
    }
    
    public static void discoverDamageType4e( String attackName, CreatureAttackDamage damage )
    {
      if( attackName.toLowerCase().contains( "claw" ) || attackName.toLowerCase().contains( "rake" ) )
      {
        damage.addQuality( new CreatureAttackQuality_Slash() );
        damage.addQuality( new CreatureAttackQuality_Pierce() );
      }
      else if( attackName.toLowerCase().contains( "bite" ) )
      {
        damage.addQuality( new CreatureAttackQuality_Slash() );
        damage.addQuality( new CreatureAttackQuality_Pierce() );
        damage.addQuality( new CreatureAttackQuality_Bash() );
      }
      else if( attackName.toLowerCase().contains( "sword" ) || attackName.toLowerCase().contains( "axe" ) || attackName.toLowerCase().contains( "blade" )
          || attackName.toLowerCase().contains( "scimitar" ) || attackName.toLowerCase().contains( "kama" ) || attackName.toLowerCase().contains( "scythe" )
          || attackName.toLowerCase().contains( "halberd" ) || attackName.toLowerCase().contains( "falchion" ) || attackName.toLowerCase().contains( "glaive" ) )
        damage.addQuality( new CreatureAttackQuality_Slash() );
      else if( attackName.toLowerCase().contains( "bow" ) || attackName.toLowerCase().contains( "arrow" ) || attackName.toLowerCase().contains( "bolt" )
          || attackName.toLowerCase().contains( "spear" ) || attackName.toLowerCase().contains( "dagger" ) || attackName.toLowerCase().contains( "pick" )
          || attackName.toLowerCase().contains( "dart" ) || attackName.toLowerCase().contains( "javelin" ) || attackName.toLowerCase().contains( "gore" ) )
        damage.addQuality( new CreatureAttackQuality_Pierce() );
      else
        damage.addQuality( new CreatureAttackQuality_Bash() );
    }
    
    public static void discoverMaterial( String damageType, CreatureAttackDamage damage )
    {
    	damageType = damageType.toLowerCase();
    	
    	if(damageType.contains("mithral") || damageType.contains("silver"))
    	  damage.addQuality( new CreatureAttackQuality_Silver() );
    	if(damageType.contains("adamantine"))
      	  damage.addQuality( new CreatureAttackQuality_Admantine() );
    	if(damageType.contains("cold iron") || damageType.contains("nexavaran steel") )
      	  damage.addQuality( new CreatureAttackQuality_Cold_Iron() );   		
    }
    
    public static void addMagicalEnhancementsIfExist( Node attackNode, ArrayList damages, CreatureAttack attack )
    {
    	for( Node child = attackNode.getFirstChild(); child != null; child = child.getNextSibling() )
        {
          if( child.getNodeType() == child.ELEMENT_NODE )
          {
        	NamedNodeMap attr = child.getAttributes();
        	String enhancementName = attr.getNamedItem( "name" ).getNodeValue().toLowerCase();
        	
        	try
            {
    		  CreatureAttackDamage damage = new CreatureAttackDamage();
	    	
    		  if(enhancementName.contains("flaming"))
    		  {
    		    damage.setDice( new Dice( "1d6" ) );
	    		damage.addQuality( new CreatureAttackQuality_Fire() );
	    		damages.add( damage );
	    		if(enhancementName.contains("burst"))
	    		{
	    		  CreatureAttackDamage burstDamage = new CreatureAttackDamage();
	    		  String numDice;
	    		  numDice = Byte.toString((byte)(attack.getCritMultiplier()-1)); 
	    		  burstDamage.setDice( new Dice( numDice + "d10" ) );
	    		  burstDamage.addQuality( new CreatureAttackQuality_Fire() );
	    		  burstDamage.addQuality( new CreatureAttackQuality_Critical() );
	    		  damages.add( burstDamage );
	    		}
    		  }
    		  
    		  if(enhancementName.contains("frost") || enhancementName.contains("icy burst"))
    		  {
    		    damage.setDice( new Dice( "1d6" ) );
	    		damage.addQuality( new CreatureAttackQuality_Cold() );
	    		damages.add( damage );
	    		if(enhancementName.contains("burst"))
	    		{
	    		  CreatureAttackDamage burstDamage = new CreatureAttackDamage();
	    		  String numDice;
	    		  numDice = Byte.toString((byte)(attack.getCritMultiplier()-1)); 
	    		  burstDamage.setDice( new Dice( numDice + "d10" ) );
	    		  burstDamage.addQuality( new CreatureAttackQuality_Cold() );
	    		  burstDamage.addQuality( new CreatureAttackQuality_Critical() );
	    		  damages.add( burstDamage );
	    		}
    		  }
    		  
    		  if(enhancementName.contains("shock"))
    		  {
    		    damage.setDice( new Dice( "1d6" ) );
	    		damage.addQuality( new CreatureAttackQuality_Electricity() );
	    		damages.add( damage );
	    		if(enhancementName.contains("burst"))
	    		{
	    		  CreatureAttackDamage burstDamage = new CreatureAttackDamage();
	    		  String numDice;
	    		  numDice = Byte.toString((byte)(attack.getCritMultiplier()-1)); 
	    		  burstDamage.setDice( new Dice( numDice + "d10" ) );
	    		  burstDamage.addQuality( new CreatureAttackQuality_Electricity() );
	    		  burstDamage.addQuality( new CreatureAttackQuality_Critical() );
	    		  damages.add( burstDamage );
	    		}
    		  }
    		  
    		  if(enhancementName.contains("corrosive"))
    		  {
    		    damage.setDice( new Dice( "1d6" ) );
	    		damage.addQuality( new CreatureAttackQuality_Acid() );
	    		damages.add( damage );
	    		if(enhancementName.contains("burst"))
	    		{
	    		  CreatureAttackDamage burstDamage = new CreatureAttackDamage();
	    		  String numDice;
	    		  numDice = Byte.toString((byte)(attack.getCritMultiplier()-1)); 
	    		  burstDamage.setDice( new Dice( numDice + "d10" ) );
	    		  burstDamage.addQuality( new CreatureAttackQuality_Acid() );
	    		  burstDamage.addQuality( new CreatureAttackQuality_Critical() );
	    		  damages.add( burstDamage );
	    		}
    		  }
    		  
    		  if(enhancementName.contains("vicious"))
    		  {
    		    damage.setDice( new Dice( "2d6" ) );
	    		damage.addQuality( new CreatureAttackQuality_Magic() );
	    		damages.add( damage );
    		  }
	    	  
    		  if(enhancementName.contains("merciful"))
    		  {
    		    damage.setDice( new Dice( "1d6" ) );
	    		damage.addQuality( new CreatureAttackQuality_Nonlethal() );
	    		damages.add( damage );
    		  }
    		  
    		  if(enhancementName.contains("holy"))
    		    ((CreatureAttackDamage)damages.get(0)).addQuality( new CreatureAttackQuality_Good() );
    		    
    		  if(enhancementName.contains("unholy"))
      		    ((CreatureAttackDamage)damages.get(0)).addQuality( new CreatureAttackQuality_Evil() );
      		  
    		  if(enhancementName.contains("axiomatic"))
      		    ((CreatureAttackDamage)damages.get(0)).addQuality( new CreatureAttackQuality_Lawful() );
      		  
    		  if(enhancementName.contains("anarchic"))
      		    ((CreatureAttackDamage)damages.get(0)).addQuality( new CreatureAttackQuality_Chaotic() );
            }
    		catch( Exception ee )
        	{
        	}	    	
          }
        }
    }
   
    public static String processIfAbilityDamage( String attackDamage )
    {
      // Remove ability name from attack damage
      // plan to eventually create effect of the type of damage once effect
      // system is tied to attacks.
      if( attackDamage.contains( "Str" ) )
      {
        attackDamage = "1d0";
      }
      if( attackDamage.contains( "Dex" ) )
      {
        attackDamage = "1d0";
      }
      if( attackDamage.contains( "Con" ) )
      {
        attackDamage = "1d0";
      }
      if( attackDamage.contains( "Int" ) )
      {
        attackDamage = "1d0";
      }
      if( attackDamage.contains( "Wis" ) )
      {
        attackDamage = "1d0";
      }
      if( attackDamage.contains( "Chr" ) )
      {
        attackDamage = "1d0";
      }

      return attackDamage;
    }
    
    public static String extractWeaponDamage( String attackDamage )
    {
      // Remove any non weapon base weapon damage
      // Anything after a space is considered extra damage
      // anything after the second + or - is considered extra damage
    	
      int plusMinusCount = 0;

      
      for(int i = 0; i < attackDamage.length(); i++)
      {
        if(attackDamage.charAt(i) == ' ')
        {
          return attackDamage.substring(0,i);          
        }
        if(attackDamage.charAt(i) == '+' || attackDamage.charAt(i) == '-')
          plusMinusCount++;
        if(plusMinusCount >=2)
          return attackDamage.substring(0,i);     	  
      }     

      return attackDamage;
    }

    public static void applyCreatureAttributes( CreatureTemplate ctr, Node data ) throws ImportCreatureException
    {
      if( "pathfinder".equalsIgnoreCase( ctr.getGameSystem() ) )
        applyPathfinderDefaultAttack( ctr );

      for( Node child = data.getFirstChild(); child != null; child = child.getNextSibling() )
      {
        if( child.getNodeType() == child.ELEMENT_NODE )
        {
          NamedNodeMap attr = child.getAttributes();
          String attackName = attr.getNamedItem( "name" ).getNodeValue();
          String attackToHit = attr.getNamedItem( "attack" ).getNodeValue();
          String attackDamage = attr.getNamedItem( "damage" ).getNodeValue();

          String attackCritical = attr.getNamedItem( "critical" ).getNodeValue();
          String attackMulti = attr.getNamedItem( "multiattacks" ).getNodeValue();
          CreatureAttackDamage damage = new CreatureAttackDamage();

          discoverDamageIsMagic(attackName,damage);
          
          Node damageNode = attr.getNamedItem( "damagetype" );
          if( "4e".equalsIgnoreCase( ctr.getGameSystem()) || damageNode == null )
            discoverDamageType4e( attackName, damage ); 
    	  else
    	    discoverDamageType( attr.getNamedItem( "damagetype" ).getNodeValue(), damage );
          
        	  
          if (attr.getNamedItem( "material" ) != null)
            discoverMaterial(attr.getNamedItem( "material" ).getNodeValue() ,damage);
          
          attackDamage = processIfAbilityDamage( attackDamage );

          try
          {
            if( attackDamage.equals( "-" ) )
              damage.setDice( new Dice( "1d0" ) );
            else if( attackDamage.length() == 1 )
              damage.setDice( new Dice( attackDamage + "d1" ) );
            else
              damage.setDice( new Dice( extractWeaponDamage(attackDamage) ) );
            
          }
          catch( Exception e )
          {
        	try
            {
              damage.setDice( new Dice( "1d0" ) );
              ctr.addToErrorLog( "Unable to set Damage: " + attackDamage 
            		  + " for attack: " + attackName);
            }
        	catch( Exception ee )
        	{
        		
        	  throw new ImportCreatureException( ee.getMessage() );
        	}
          }

          CreatureAttack attack = new CreatureAttack();
          attack.setName( attackName );
          attack.setToHit( Short.parseShort( attackToHit ) );
          if( "4thEd".equalsIgnoreCase( ctr.getGameSystem() ) ) // If 4th ed set
                                                                // crit to max
            attack.setCritMultiplier( (byte)1 );
          else
            resolveCrit( ctr, attack, attackCritical );

          attack.setStyle( resolveStyle( attr ) );

          attack.assumeType( resolveType( attr ) );
          attack.setAttackCascading( "yes".equals( attackMulti ) );

          ArrayList damages = new ArrayList();
          damages.add( damage );
          //check for known enhancement damages
          addMagicalEnhancementsIfExist(child,damages,attack);
          
          attack.setDamages( damages );
          ctr.getAttacks().add( attack );

          // If it is a multi attack listing add it again Example claw x2
          // Creatures that have this usually go in groups of 2... up to 6 for
          // the maralith
          if( attackName.toLowerCase().contains( "x2" ) )
            ctr.getAttacks().add( attack );
          if( attackName.toLowerCase().contains( "x4" ) )
          {
            ctr.getAttacks().add( attack );
            ctr.getAttacks().add( attack );
            ctr.getAttacks().add( attack );
          }
          if( attackName.toLowerCase().contains( "x6" ) )
          {
            ctr.getAttacks().add( attack );
            ctr.getAttacks().add( attack );
            ctr.getAttacks().add( attack );
            ctr.getAttacks().add( attack );
            ctr.getAttacks().add( attack );
          }

        }
      }
    }

    private static void resolveCrit( CreatureTemplate ctr, CreatureAttack attack, String crit )
    {
      try
      {
	    for(int i = 0; i < crit.length(); i++)
	    {
	    	if((int)crit.charAt(i) < 45 || (int)crit.charAt(i) > 122 )
	    		crit = crit.replace(crit.charAt(i),'x');
	    }
    		
        if( !crit.contains( "x" )  )
        {
          attack.setCritMultiplier( (byte)1 );
          return;
        }
        
        String range;
        String mult;
      	  
        StringTokenizer sToke;
        if (crit.contains("/x"))
        {
      	  sToke = new StringTokenizer( crit, "/x" );
      	  range = sToke.nextToken();
        }
        else
        {
      	  sToke = new StringTokenizer(crit, "x");
          range = "20";
        }
        
        mult = sToke.nextToken();
  
        if(!"20".equals( range ) )
        {
          attack.setCritMinThreat( Byte.parseByte( range.substring( 0, range.indexOf( "-" ) ) ) );
        }
  
        attack.setCritMultiplier( Byte.parseByte( mult ) );
      }
      catch( Exception e )
      {
        String msg = "Failed to resolve Crit: " + crit;
        ctr.addToErrorLog( msg );
        LoggingManager.severe( AttackLogic.class, msg, e );
      }
    }

    private static CreatureAttackStyle resolveStyle( NamedNodeMap attr )
    {
      String attackEquipped = attr.getNamedItem( "equipped" ).getNodeValue();
      if( "both".equals( attackEquipped ) )
        return new CreatureAttackStyle_2Hand();
      if( "secondary".equals( attackEquipped ) )
        return new CreatureAttackStyle_OffHand();
      // else "primary"
      return new CreatureAttackStyle_1Hand();
    }

    private static CreatureAttackType resolveType( NamedNodeMap attr )
    {
      if( isOn( attr, "ranged" ) )
        return new CreatureAttackType_Range();
      if( isOn( attr, "thrown" ) )
        return new CreatureAttackType_Thrown();
      if( isOn( attr, "finesse" ) )
        return new CreatureAttackType_Finesse();
      return new CreatureAttackType_Melee();
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
          String featName = attr.getNamedItem( "name" ).getNodeValue();
          if( Feat_InitModifier.IMPROVED_INIT.equalsIgnoreCase( featName ) )
          {
            feats.add( Feat_InitModifier.buildStandard() );
          }
          else
          {
            feats.add( new GenericFeat( featName ) );
          }
        }
      }
      ctr.getFeats().setFeats( (GenericFeat[])feats.toArray( new GenericFeat[ 0 ] ) );
    }
  }

  static class SkillLogic
  {
    // return -10 if skill not found
    public static short getModFromSkill( CreatureTemplate ctr, String skillName )
    {

      // Charisma based skills
      if( "Bluff".equals( skillName ) || "Diplomacy".equals( skillName ) || "Intimidate".equals( skillName ) || "Streetwise".equals( skillName ) )
      {
        return (short)((ctr.getAbilityScore( D20Rules.Ability.CHA ) - 10) / 2);
      }

      // Constitution based skills
      if( "Endurance".equals( skillName ) )
      {
        return (short)((ctr.getAbilityScore( D20Rules.Ability.CON ) - 10) / 2);
      }

      // Dex based skills
      if( "Acrobatics".equals( skillName ) || "Stealth".equals( skillName ) || "Thievery".equals( skillName ) )
      {
        return (short)((ctr.getAbilityScore( D20Rules.Ability.DEX ) - 10) / 2);
      }

      // Int based skills
      if( "Arcana".equals( skillName ) || "History".equals( skillName ) || "Religion".equals( skillName ) )
      {
        return (short)((ctr.getAbilityScore( D20Rules.Ability.INT ) - 10) / 2);
      }

      // STR based skills
      if( "Athletics".equals( skillName ) )
      {
        return (short)((ctr.getAbilityScore( D20Rules.Ability.STR ) - 10) / 2);
      }
      // WIS based skills
      if( "Dungeoneering".equals( skillName ) || "Heal".equals( skillName ) || "Insight".equals( skillName ) || "Nature".equals( skillName )
          || "Perception".equals( skillName ) )
      {
        return (short)((ctr.getAbilityScore( D20Rules.Ability.WIS ) - 10) / 2);
      }

      return -10; // Skill not found in list
    }

    public static void applyCreatureAttributes( CreatureImportServices svc, CreatureTemplate ctr, Node data, String gameSystem ) throws ImportCreatureException
    {
      SkillBinder binder = svc.accessSkills();

      ArrayList<GenericSkill> skills = new ArrayList<GenericSkill>();
      for( Node child = data.getFirstChild(); child != null; child = child.getNextSibling() )
      {
        if( child.getNodeType() == Node.ELEMENT_NODE )
        {
          NamedNodeMap attr = child.getAttributes();
          String skillName = attr.getNamedItem( "name" ).getNodeValue();
          String skillRanks = "0";
          String skillMisc = "0";

          if( gameSystem.equalsIgnoreCase( "4e" ) )
          {
            String skillStrRanks = attr.getNamedItem( "value" ).getNodeValue();
            short skillMod = getModFromSkill( ctr, skillName );

            if( skillMod != -10 )
            {
              short shSkillRanks = (short)(Short.parseShort( skillStrRanks ) - skillMod);
              short level = (short)(ctr.getClasses().resolveLevel() / 2);
              short misc = (short)(shSkillRanks - level);

              GenericSkillTemplate skillTemplate = binder.accessSkill( skillName );
              if( null != skillTemplate )
              {
                skills.add( new GenericSkill( skillTemplate, level, misc ) );
              }
              else
              {
                ctr.addToErrorLog( "Unknown skill: " + skillName + " Modifer: " + skillRanks );
              }
            }
            continue;
          }
          else
          {
            skillRanks = attr.getNamedItem( "ranks" ).getNodeValue();
            skillMisc = attr.getNamedItem( "misc" ).getNodeValue();
            
            if(skillRanks.contains(".5"))
            	skillRanks = skillRanks.replace(".5", "");
          }
          Short Pathfinder_Trained_Modifier = 0;
          
          
          if( ctr.getGameSystem().equalsIgnoreCase( "pathfinder" ) )
          {
        	// if classskill does not exist use old logic
        	if (attr.getNamedItem( "classskill" ) ==  null)
        	{
        	  if( Short.parseShort( skillRanks ) > 0 )
        	    Pathfinder_Trained_Modifier = 3;
        	}
            else
            {
              if( attr.getNamedItem( "classskill" ).getNodeValue().equalsIgnoreCase("yes") &&
            		  Short.parseShort( skillRanks ) > 0 )
                Pathfinder_Trained_Modifier = 3;            	   	
            }
          }

        
          // Modify the text of Knowledge skills to match d20Pro strings
          if( skillName.contains( "Knowledge:" ) )
          {
            skillName = skillName.replace( ": ", " (" );
            skillName = skillName + ")";
          }
          GenericSkillTemplate skillTemplate = binder.accessSkill( skillName );
          if( null != skillTemplate )
          {
            Short valueMisc = new Short( (short)(Short.parseShort( skillMisc ) + Pathfinder_Trained_Modifier.shortValue()) );

            skills.add( new GenericSkill( skillTemplate, Short.parseShort( skillRanks ), valueMisc ) );
          }
          else
          {
            ctr.addToErrorLog( "Unknown skill: " + skillName + " Ranks: " + skillRanks + " Misc Mod: " + skillMisc );
          }
        }
      }

      ctr.getSkills().setSkills( skills.toArray( new GenericSkill[ skills.size() ] ) );
    }
  }

  static class GearLogic
  {
    public static void applyCreatureAttributes( CreatureTemplate ctr, Node data ) throws ImportCreatureException
    {
      CreatureTemplate_Items items = ctr.getItems();
      for( Node child = data.getFirstChild(); child != null; child = child.getNextSibling() )
      {
        if( child.getNodeType() == Node.ELEMENT_NODE )
        {
          ItemTemplate item = new ItemTemplate();

          NamedNodeMap attr = child.getAttributes();
          item.assignName( attr.getNamedItem( "name" ).getNodeValue() );
          item.assignQuantity( attr.getNamedItem( "quantity" ).getNodeValue() );
          Node weightNode = attr.getNamedItem( "weight" );
          if( null != weightNode )
          {
            String weightInLbs = weightNode.getNodeValue();
            if( weightInLbs.toLowerCase().contains( "lb" ) )
              weightInLbs = weightInLbs.substring( 0, weightInLbs.indexOf( "lb" ) );
            float weight = Float.parseFloat( weightInLbs ) / item.resolveQuantity();
            item.assignWeight( weight );
          }
          items.addItem( item );
        }
      }
    }
  }

  static class UserImagesLogic
  {
     static void applyCreatureAttributes( CreatureTemplate ctr, Node data, ImageImportService imageSvc ) throws ImportCreatureException
     {
       int index = 0;
       for( Node child = data.getFirstChild(); child != null; child = child.getNextSibling() )
       {
         if( child.getNodeType() == Node.ELEMENT_NODE )
         {
           NamedNodeMap attr = child.getAttributes();
           
           String encoding = attr.getNamedItem( "encoding" ).getNodeValue();
           if( "base64".equals( encoding ) )
           {
             String encodedImage = child.getTextContent();
             introduceImage( ctr, encodedImage, imageSvc, index );
           }
           else
           {
             ctr.addToErrorLog( "Unsupported user image encoding: " + encoding );
           }
           ++index;
         }
       }
     }
     
     private static void introduceImage( CreatureTemplate ctr, String encodedImage, ImageImportService imageSvc, int index )
     {
       try
       {
         byte[] decodedImage = ObjectLibrary.base64DecodeString( encodedImage );
         
         JLabel imageObs = new JLabel();
         Image img = new ImageIcon( decodedImage ).getImage();
         if( !D20ImageUtil.isSquare( img, imageObs ) )
         {
           img = D20ImageUtil.makeSquare( img, Color.WHITE, imageObs );
         }
         
         String filename = String.format( "%s%02d", ctr.getName(), Integer.valueOf( index ) );
         filename = FileLibrary.encodeIllegalCharacters( filename );
         ImageImportUtil importUtil = new ImageImportUtil( imageSvc, ImageProvider.Categories.CREATURE, filename, "HeroLab", D20ImageUtil.Format.PNG.asExtensionWithDot() );
         short imageId = importUtil.findExisting( img, imageObs, D20ImageUtil.Format.PNG );
         if( imageId == ImageProvider.ANONYMOUS )
         {
           importUtil.resolve();
           importUtil.writeToFile( img, imageObs, D20ImageUtil.Format.PNG );
           imageId = importUtil.importCategoryImage();
         }
         
         if( index == 0 )
         {
           ctr.setImageID( imageId );
         }
       }
       catch( Exception e )
       {
         ctr.addToErrorLog( "Failed in introduce image", e );
       }
     }
  }
  
  static class SpecialAbilityLogic
  {
    private static CreatureAttackQuality AttackQualityFromString( String qualName )
    {
      CreatureAttackQuality quality = new CreatureAttackQuality_Dash();

      if( qualName.equalsIgnoreCase( "adamantine" ) )
        return new CreatureAttackQuality_Admantine();
      if( qualName.equalsIgnoreCase( "bludgeoning" ) )
        return new CreatureAttackQuality_Bash();
      if( qualName.equalsIgnoreCase( "cold iron" ) )
        return new CreatureAttackQuality_Cold_Iron();
      if( qualName.equalsIgnoreCase( "epic" ) )
        return new CreatureAttackQuality_Epic();
      if( qualName.equalsIgnoreCase( "evil" ) )
        return new CreatureAttackQuality_Evil();
      if( qualName.equalsIgnoreCase( "good" ) )
        return new CreatureAttackQuality_Good();
      if( qualName.equalsIgnoreCase( "lawful" ) )
        return new CreatureAttackQuality_Lawful();
      if( qualName.equalsIgnoreCase( "magic" ) )
        return new CreatureAttackQuality_Magic();
      if( qualName.equalsIgnoreCase( "piercing" ) )
        return new CreatureAttackQuality_Pierce();
      if( qualName.equalsIgnoreCase( "silver" ) )
        return new CreatureAttackQuality_Silver();
      if( qualName.equalsIgnoreCase( "slashing" ) )
        return new CreatureAttackQuality_Slash();
      if( qualName.equalsIgnoreCase( "-" ) )
        return null;

      return quality;
    }

    private static void processDamageReduction( CreatureTemplate ctr, String DR )
    {
      LoggingManager.debug( SpecialAbilityLogic.class, "Processing DR: " + DR );
      try
      {
        int slash = DR.indexOf( "/" );
        int open = DR.indexOf( "(" );
        int close = DR.indexOf( ")" );
        String DRType = DR.substring( slash + 1, close );
        String DRValue = DR.substring( open + 1, slash );
  
        CreatureDamageReduction theDR = ctr.getDR();
        CreatureAttackQuality quality = AttackQualityFromString( DRType );
  
        if( theDR == null )
        {
          // Create new DR
          theDR = new CreatureDamageReduction();
          if( quality != null )
            theDR.addReductionQuality( quality );
          theDR.setReductionAmount( Integer.parseInt( DRValue ) );
        }
        else
        {
          // If same DR value Append AttackQuality
          if( theDR.getReductionAmount() == Integer.parseInt( DRValue ) )
            theDR.addReductionQuality( quality );
  
          // New DR is higher replace it
          if( theDR.getReductionAmount() < Integer.parseInt( DRValue ) )
          {
            // Create new DR
            theDR = new CreatureDamageReduction();
            if( quality != null )
              theDR.addReductionQuality( quality );
            theDR.setReductionAmount( Integer.parseInt( DRValue ) );
          }
        }
        ctr.setDR( theDR );
      }
      catch( Exception e )
      {
        String msg = "Failed to process DR: " + DR;
        ctr.addToErrorLog( msg );
        LoggingManager.severe( SpecialAbilityLogic.class, msg, e );
      }
    }

    private static void processDamageResistance( CreatureTemplate ctr, String ER )
    {
      // Damage Resistance, Electricity (10)
      int open = ER.indexOf( "(" );
      int close = ER.indexOf( ")" );
      String ERType = ER.substring( 19, open - 1 );
      String ERValue = ER.substring( open + 1, close );

      // Set Resistance by type
      if( ERType.equalsIgnoreCase( "acid" ) )
        ctr.getER().accessAcid().setResistance( Integer.parseInt( ERValue ) );
      if( ERType.equalsIgnoreCase( "cold" ) )
        ctr.getER().accessCold().setResistance( Integer.parseInt( ERValue ) );
      if( ERType.equalsIgnoreCase( "electricity" ) )
        ctr.getER().accessElectricity().setResistance( Integer.parseInt( ERValue ) );
      if( ERType.equalsIgnoreCase( "fire" ) )
        ctr.getER().accessFire().setResistance( Integer.parseInt( ERValue ) );
      if( ERType.equalsIgnoreCase( "sonic" ) )
        ctr.getER().accessSonic().setResistance( Integer.parseInt( ERValue ) );
    }

    private static void processDamageImmunity( CreatureTemplate ctr, String ER )
    {
      boolean immune = ER.toLowerCase().contains( "immunity to" );
      boolean vulnerable = ER.toLowerCase().contains( "vulnerability to" );

      // Set Immunity by type
      if( ER.toLowerCase().contains( "acid" ) )
      {
        ctr.getER().accessAcid().setImmunity( immune );
        ctr.getER().accessAcid().setVulnerability( vulnerable );
      }
      if( ER.toLowerCase().contains( "cold" ) )
      {
        ctr.getER().accessCold().setImmunity( immune );
        ctr.getER().accessCold().setVulnerability( vulnerable );
      }
      if( ER.toLowerCase().contains( "electricity" ) )
      {
        ctr.getER().accessElectricity().setImmunity( immune );
        ctr.getER().accessElectricity().setVulnerability( vulnerable );
      }
      if( ER.toLowerCase().contains( "fire" ) )
      {
        ctr.getER().accessFire().setImmunity( immune );
        ctr.getER().accessFire().setVulnerability( vulnerable );
      }
      if( ER.toLowerCase().contains( "sonic" ) )
      {
        ctr.getER().accessSonic().setImmunity( immune );
        ctr.getER().accessSonic().setVulnerability( vulnerable );
      }
    }

    private static void processRegeneration( CreatureTemplate ctr, String Regeneration )
    {
      try
      {
        // Regeneration 15
        // Fast Healing 5 (Ex)
        String amount;
        int open = Regeneration.indexOf( "(" );
        boolean Regen = Regeneration.toLowerCase().contains( "regeneration" );
  
        if( Regen ) // Is regeneration
        {
          ctr.setRegenerates( true );
          amount = Regeneration.substring( 13 );
        }
        else
        {
          ctr.setRegenerates( false );
          amount = Regeneration.substring( 13, open - 1 );
        }
  
        ctr.setFastHeal( Integer.parseInt( amount ) );
      }
      catch( Exception e )
      {
        String msg = "Failed to process regeneration: " + Regeneration;
        ctr.addToErrorLog( msg );
        LoggingManager.severe( SpecialAbilityLogic.class, msg, e );
      }
    }

    private static void checkForSpecialAbility( CreatureTemplate ctr, NamedNodeMap attr )
    {
      String name = attr.getNamedItem( "name" ).getNodeValue();
      // First see if the special ability is a DR ER or regen/fast heal
      // If true then add to proper area and return without adding to
      // specialability area
      if( name.toLowerCase().contains( "damage reduction" ) )
      {
        processDamageReduction( ctr, name );
      }
      if( name.toLowerCase().contains( "damage resistance" )|| name.toLowerCase().contains( "energy resistance" ) )
      {
        processDamageResistance( ctr, name );
      }
      if( name.toLowerCase().contains( "immunity to" ) || name.toLowerCase().contains( "vulnerability to" ) )
      {
        processDamageImmunity( ctr, name );
      }
      if( name.toLowerCase().contains( "regeneration" ) || name.toLowerCase().contains( "fast heal" ) )
      {
        processRegeneration( ctr, name );
      }
    }

    private static void addAbilityToCreature( CreatureTemplate ctr, NamedNodeMap attr )
    {
      byte frequency = 1;
      String name = attr.getNamedItem( "name" ).getNodeValue();
      String uses = attr.getNamedItem( "uses" ).getNodeValue();

      Node freqNode = attr.getNamedItem( "frequency" );

      // If Uses has the value 0 the it is an at-will power
      if( uses.equalsIgnoreCase( "0" ) )
        frequency = 0;
      if( freqNode != null )
      {
        String usage = freqNode.getNodeValue();

        if( "At-Will".equalsIgnoreCase( usage ) )
          frequency = 0;
        else if( "Encounter".equalsIgnoreCase( usage ) )
          frequency = 2;
        else if( "Daily".equalsIgnoreCase( usage ) )
          frequency = 1;
        else
          frequency = 3; // charge
      }
      
      SpecialAbility ability = new SpecialAbility();
      ability.setName( name );
      ability.setUsesTotal( Short.parseShort( uses ) );
      ability.setUsesRemain( Short.parseShort( uses ) );
      ability.setUseMode( frequency );
      
      /*
       * Thraxxis writes:
       * It appears the that HeroLabImportEffectLogic was never added to version control.
       * I've crippled this so the code will compile.
      
      // Attempt to add known abilities
      HeroLabImportEffectLogic effectBuilder = new HeroLabImportEffectLogic();
      ability = effectBuilder.attemptToBuildEffect(ctr, ability);
      
      
      try
      {
        ctr.getSpecialAbilities().addAbility( ability );
      }
      catch( Exception e )
      {
        ctr.addToErrorLog( "Error adding Special Ability: " + name, e );
      }
      
      */
    }

    static void applyCreatureAttributes( CreatureTemplate ctr, Node data ) throws ImportCreatureException
    {
      for( Node child = data.getFirstChild(); child != null; child = child.getNextSibling() )
      {
        if( child.getNodeType() == Node.ELEMENT_NODE )
        {
          NamedNodeMap attr = child.getAttributes();

          // Check to see if an ability should be added to the creature
          Node uses = attr.getNamedItem( "uses" );

          // If there are uses added to abilities tab
          if( uses != null )
            addAbilityToCreature( ctr, attr );
          else
            checkForSpecialAbility( ctr, attr );

          // Add details of ability to the notes tab
          try
          {
            ctr.addToAbilitiesNotesLog( attr.getNamedItem( "name" ).getNodeValue() + ": " + child.getFirstChild().getNodeValue() );
          }
          catch( Exception e )
          {
            String msg = "Unable to add ability";
            LoggingManager.severe( HeroLabImportLogic.class, msg, e );
            ctr.addToErrorLog( msg );
          }
        }
      }
    }
  }

  static class BackgroundLogic
  {
    public static void applyCreatureAttributes( CreatureTemplate ctr, Node data ) throws ImportCreatureException
    {
      for( Node child = data.getFirstChild(); child != null; child = child.getNextSibling() )
      {
        if( child.getNodeType() == Node.CDATA_SECTION_NODE )
        {
          ctr.addToBackground( child.getNodeValue() );
        }
      }
    }
  }

  static class DamageReductionLogic
  {
    public static void applyCreatureAttributes( CreatureTemplate ctr, Node data ) throws ImportCreatureException
    {
      boolean anotherType = false;

      for( Node child = data.getFirstChild(); child != null; child = child.getNextSibling() )
      {
        if( child.getNodeType() == child.ELEMENT_NODE )
        {
          NamedNodeMap attr = child.getAttributes();
          String DRName = attr.getNamedItem( "name" ).getNodeValue();
          String DRValue = attr.getNamedItem( "value" ).getNodeValue();
          String type = attr.getNamedItem( "type" ).getNodeValue();

          CreatureDamageReduction dr = new CreatureDamageReduction();

          if( anotherType )
            dr = ctr.getDR();

          dr.setReductionAmount( Integer.parseInt( DRValue ) );

          if( type.length() > 0 )
            dr.setAndDR( "and".equals( type ) );

          if( DRName.length() > 0 )
          {
            if( "adamantine".equals( DRName ) )
            {
              CreatureAttackQuality_Admantine DRType = new CreatureAttackQuality_Admantine();
              dr.addReductionQuality( DRType );
            }
            if( "bludgeoning".equals( DRName ) )
            {
              CreatureAttackQuality_Bash DRType = new CreatureAttackQuality_Bash();
              dr.addReductionQuality( DRType );
            }
            if( "cold iron".equals( DRName ) )
            {
              CreatureAttackQuality_Cold_Iron DRType = new CreatureAttackQuality_Cold_Iron();
              dr.addReductionQuality( DRType );
            }
            if( "epic".equals( DRName ) )
            {
              CreatureAttackQuality_Epic DRType = new CreatureAttackQuality_Epic();
              dr.addReductionQuality( DRType );
            }
            if( "evil".equals( DRName ) )
            {
              CreatureAttackQuality_Evil DRType = new CreatureAttackQuality_Evil();
              dr.addReductionQuality( DRType );
            }
            if( "good".equals( DRName ) )
            {
              CreatureAttackQuality_Good DRType = new CreatureAttackQuality_Good();
              dr.addReductionQuality( DRType );
            }
            if( "lawful".equals( DRName ) )
            {
              CreatureAttackQuality_Lawful DRType = new CreatureAttackQuality_Lawful();
              dr.addReductionQuality( DRType );
            }
            if( "magic".equals( DRName ) )
            {
              CreatureAttackQuality_Magic DRType = new CreatureAttackQuality_Magic();
              dr.addReductionQuality( DRType );
            }
            if( "piercing".equals( DRName ) )
            {
              CreatureAttackQuality_Pierce DRType = new CreatureAttackQuality_Pierce();
              dr.addReductionQuality( DRType );
            }
            if( "silver".equals( DRName ) )
            {
              CreatureAttackQuality_Silver DRType = new CreatureAttackQuality_Silver();
              dr.addReductionQuality( DRType );
            }
            if( "slashing".equals( DRName ) )
            {
              CreatureAttackQuality_Slash DRType = new CreatureAttackQuality_Slash();
              dr.addReductionQuality( DRType );
            }

            ctr.setDR( dr );
            anotherType = true;
          }
        }
      }
    }
  }

  static class ElementalResistancesLogic
  {
    public static void applyCreatureAttributes( CreatureTemplate ctr, Node data ) throws ImportCreatureException
    {
      for( Node child = data.getFirstChild(); child != null; child = child.getNextSibling() )
      {
        if( child.getNodeType() == child.ELEMENT_NODE )
        {
          NamedNodeMap attr = child.getAttributes();
          String resistanceName = attr.getNamedItem( "name" ).getNodeValue();
          String resistValue = attr.getNamedItem( "value" ).getNodeValue();
          String immune = attr.getNamedItem( "immune" ).getNodeValue();
          String vulnerable = attr.getNamedItem( "vulnerable" ).getNodeValue();

          if( resistValue.length() == 0 )
            resistValue = "0";

          if( resistanceName.equals( "Fire" ) )
          {
            ctr.getER().accessFire().setResistance( Integer.parseInt( resistValue ) );
            ctr.getER().accessFire().setImmunity( "true".equals( immune ) );
            ctr.getER().accessFire().setVulnerability( "true".equals( vulnerable ) );
          }
          if( resistanceName.equals( "Cold" ) )
          {
            ctr.getER().accessCold().setResistance( Integer.parseInt( resistValue ) );
            ctr.getER().accessCold().setImmunity( "true".equals( immune ) );
            ctr.getER().accessCold().setVulnerability( "true".equals( vulnerable ) );
          }

          if( resistanceName.equals( "Acid" ) )
          {
            ctr.getER().accessAcid().setResistance( Integer.parseInt( resistValue ) );
            ctr.getER().accessAcid().setImmunity( "true".equals( immune ) );
            ctr.getER().accessAcid().setVulnerability( "true".equals( vulnerable ) );
          }
          if( resistanceName.equals( "Electricity" ) )
          {
            ctr.getER().accessElectricity().setResistance( Integer.parseInt( resistValue ) );
            ctr.getER().accessElectricity().setImmunity( "true".equals( immune ) );
            ctr.getER().accessElectricity().setVulnerability( "true".equals( vulnerable ) );
          }
          if( resistanceName.equals( "Sonic" ) )
          {
            ctr.getER().accessSonic().setResistance( Integer.parseInt( resistValue ) );
            ctr.getER().accessSonic().setImmunity( "true".equals( immune ) );
            ctr.getER().accessSonic().setVulnerability( "true".equals( vulnerable ) );
          }
        }
      }
    }
  }

  static class Defense4eLogic
  {
    public static void applyCreatureAttributes( CreatureTemplate ctr, Node data ) throws ImportCreatureException
    {
      for( Node child = data.getFirstChild(); child != null; child = child.getNextSibling() )
      {
        if( child.getNodeType() == child.ELEMENT_NODE )
        {
          NamedNodeMap attr = child.getAttributes();
          String defenseName = attr.getNamedItem( "name" ).getNodeValue();
          String totalValue = attr.getNamedItem( "total" ).getNodeValue();

          if( defenseName.equalsIgnoreCase( "fortitude" ) )
            ctr.getCustomDefense()[0] = Short.parseShort( totalValue.trim() );

          if( defenseName.equalsIgnoreCase( "reflex" ) )
            ctr.getCustomDefense()[1] = Short.parseShort( totalValue.trim() );

          if( defenseName.equalsIgnoreCase( "will" ) )
            ctr.getCustomDefense()[2] = Short.parseShort( totalValue.trim() );

          if( defenseName.equalsIgnoreCase( "armor class" ) )
          {
            // Set the AC value
            byte[] _ac = new byte[ 6 ];

            for( int i = 0; i < 6; i++ )
              _ac[i] = 0;

            for( Node child2 = child.getFirstChild(); child2 != null; child2 = child2.getNextSibling() )
            {
              if( child2.getNodeType() == child2.ELEMENT_NODE )
              {
                NamedNodeMap attr2 = child2.getAttributes();
                String partName = attr2.getNamedItem( "name" ).getNodeValue();
                String partValue = attr2.getNamedItem( "value" ).getNodeValue();

                if( partName.equalsIgnoreCase( "ability Bonus" ) )
                  _ac[0] = Byte.parseByte( partValue );

                if( partName.equalsIgnoreCase( "armor bonus" ) )
                  _ac[1] = Byte.parseByte( partValue );

                if( partName.equalsIgnoreCase( "shield bonus" ) )
                  _ac[2] = Byte.parseByte( partValue );

                if( partName.equalsIgnoreCase( "enhancement bonus" ) )
                  _ac[4] += Byte.parseByte( partValue );

                if( partName.equalsIgnoreCase( "class bonus" ) )
                  _ac[4] += Byte.parseByte( partValue );

                if( partName.equalsIgnoreCase( "feat bonus" ) )
                  _ac[4] += Byte.parseByte( partValue );

                if( partName.equalsIgnoreCase( "power bonus" ) )
                  _ac[4] += Byte.parseByte( partValue );

                if( partName.equalsIgnoreCase( "proficiency bonus" ) )
                  _ac[4] += Byte.parseByte( partValue );

                if( partName.equalsIgnoreCase( "racial bonus" ) )
                  _ac[4] += Byte.parseByte( partValue );
              }
            }
            ctr.setAC( _ac );
            ctr.setMaxDexBonus( (short)0 );
          }
        }
      }
    }
  }

  static class Power4eLogic
  {
    public static short setAttackStat( CreatureTemplate ctr, CreatureAttack attack, String attackStat, String damageStat )
    {

      if( damageStat.toLowerCase().contains( "str" ) )
        attack.setAbilityToDamage( D20Rules.Ability.STR );

      if( damageStat.toLowerCase().contains( "dex" ) )
        attack.setAbilityToDamage( D20Rules.Ability.DEX );

      if( damageStat.toLowerCase().contains( "con" ) )
        attack.setAbilityToDamage( D20Rules.Ability.CON );

      if( damageStat.toLowerCase().contains( "int" ) )
        attack.setAbilityToDamage( D20Rules.Ability.INT );

      if( damageStat.toLowerCase().contains( "wis" ) )
        attack.setAbilityToDamage( D20Rules.Ability.WIS );

      if( damageStat.toLowerCase().contains( "cha" ) )
        attack.setAbilityToDamage( D20Rules.Ability.CHA );

      if( attackStat.toLowerCase().contains( "str" ) )
      {
        attack.setAbilityToHit( D20Rules.Ability.STR );
        return (short)((ctr.getAbilityScore( D20Rules.Ability.STR ) - 10) / 2);
      }

      if( attackStat.toLowerCase().contains( "dex" ) )
      {
        attack.setAbilityToHit( D20Rules.Ability.DEX );
        return (short)((ctr.getAbilityScore( D20Rules.Ability.DEX ) - 10) / 2);
      }

      if( attackStat.toLowerCase().contains( "con" ) )
      {
        attack.setAbilityToHit( D20Rules.Ability.CON );
        return (short)((ctr.getAbilityScore( D20Rules.Ability.CON ) - 10) / 2);
      }

      if( attackStat.toLowerCase().contains( "int" ) )
      {
        attack.setAbilityToHit( D20Rules.Ability.INT );
        return (short)((ctr.getAbilityScore( D20Rules.Ability.INT ) - 10) / 2);
      }

      if( attackStat.toLowerCase().contains( "wis" ) )
      {
        attack.setAbilityToHit( D20Rules.Ability.WIS );
        return (short)((ctr.getAbilityScore( D20Rules.Ability.WIS ) - 10) / 2);
      }

      if( attackStat.toLowerCase().contains( "cha" ) )
      {
        attack.setAbilityToHit( D20Rules.Ability.CHA );
        return (short)((ctr.getAbilityScore( D20Rules.Ability.CHA ) - 10) / 2);
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

    public static void applyAttack( CreatureTemplate ctr, NamedNodeMap attr )
    {
      String powerName = attr.getNamedItem( "name" ).getNodeValue();
      String usage = attr.getNamedItem( "usage" ).getNodeValue();
      String attackName = attr.getNamedItem( "weapon" ).getNodeValue();
      String attackBonus = attr.getNamedItem( "attackvalue" ).getNodeValue();
      // String attackBonus = attr.getNamedItem( "attackmod" ).getNodeValue();
      String attackStat = attr.getNamedItem( "attackstat" ).getNodeValue();
      String attackDamage = attr.getNamedItem( "damage" ).getNodeValue();
      String damagemod = attr.getNamedItem( "damagemod" ).getNodeValue();
      String damageStat = attr.getNamedItem( "damagestat" ).getNodeValue();
      String attackDamageType = attr.getNamedItem( "damagetype" ).getNodeValue();
      String attackDefense = attr.getNamedItem( "defense" ).getNodeValue();

      // <power name='Rage Strike' usage='Daily' uses='2' weapon='Battle Spirit
      // Greataxe +3' attackvalue='15' attackmod='5' attackstat='Strength'
      // damage='5d12+8' damagemod='3' damagestat='Strength' damagetype=''
      // defense='Armor Class'/>
      if( usage.equalsIgnoreCase( "Encounter" ) )
        powerName = "*" + powerName;

      if( usage.equalsIgnoreCase( "Daily" ) )
        powerName = "**" + powerName;

      CreatureAttack attack = new CreatureAttack();
      attack.assumeType( new CreatureAttackType_Melee() );
      short statMod = setAttackStat( ctr, attack, attackStat, damageStat );

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
        try
        {
          damage.setDice( new Dice( "1d1" ) );
        }
        catch( DiceFormatException dfe )
        {
          ctr.addToErrorLog( "Problem with dice", dfe );
        }
      }

      // For basic attacks list just the weapon name
      if( powerName.contains( "Basic Melee" ) || powerName.contains( "Basic Ranged" ) )
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

    public static void applyCreatureAttributes( CreatureTemplate ctr, Node data ) throws ImportCreatureException
    {
      for( Node child = data.getFirstChild(); child != null; child = child.getNextSibling() )
      {
        if( child.getNodeType() == child.ELEMENT_NODE )
        {
          NamedNodeMap attr = child.getAttributes();
          String name = attr.getNamedItem( "name" ).getNodeValue();
          String usage = attr.getNamedItem( "usage" ).getNodeValue();
          if( usage.equalsIgnoreCase( "Feature" ) ) // Do nothing with features
            continue;

          // If the power has a weapon then process as a weapon
          if (attr.getNamedItem("weapon") != null)
          {
        	  String weapon = attr.getNamedItem( "weapon" ).getNodeValue();
        	  if( weapon.length() > 0 ) // process attack
        		  applyAttack( ctr, attr ); // make procedure call
          }
          if( usage.equalsIgnoreCase( "At-Will" ) )
          {
            if( name.equalsIgnoreCase( "basic melee" ) || name.equalsIgnoreCase( "basic ranged" ) )
              continue;
            SpecialAbility ability = new SpecialAbility();
            ability.setName( name );
            ability.setUsesTotal( (short)1 );
            ability.setUsesRemain( (short)1 );
            ability.setUseMode( (short)0 ); // 0 is at-will

            try
            {
              ctr.getSpecialAbilities().addAbility( ability );
              // Remove default ability if one was added
              ctr.getSpecialAbilities().deleteAbility( "spontaneous ability" );
            }
            catch( Exception e )
            {
              ctr.addToErrorLog( "Error Special Ability: " + name, e );
            }
          }
          else
          {
            String uses = attr.getNamedItem( "uses" ).getNodeValue();
            byte frequency = 3; // Default to a charge item

            if( "Encounter".equalsIgnoreCase( usage ) )
              frequency = 2;
            else if( "Daily".equalsIgnoreCase( usage ) )
              frequency = 1;

            SpecialAbility ability = new SpecialAbility();
            ability.setName( name );
            ability.setUsesTotal( Short.parseShort( uses ) );
            ability.setUsesRemain( Short.parseShort( uses ) );
            ability.setUseMode( frequency ); // 0 is at-will

            try
            {
              ctr.getSpecialAbilities().addAbility( ability );
              // Remove default ability if one was added
              ctr.getSpecialAbilities().deleteAbility( "spontaneous ability" );
            }
            catch( Exception e )
            {
              ctr.addToErrorLog( "Error Special Ability: " + name, e );
            }

          }
        }
      }
    }
  }

}
