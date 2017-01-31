package com.mesamundi.d20pro.herolab;

import java.io.File;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.d20pro.plugin.api.*;
import org.apache.log4j.Logger;

import com.mesamundi.common.ObjectCommon;
import com.mesamundi.common.util.Zipper;

import com.mindgene.common.util.FileFilterForExtension;
import com.mindgene.d20.common.creature.CreatureTemplate;

/**
 * Imports Creatures directly from Hero Lab portfolio files.
 * 
 * @author thraxxis
 *
 */
public class HeroLabImporter implements ImportCreaturePlugin
{
  private static final Logger lg = Logger.getLogger(HeroLabImporter.class);
  
  @Override
  public String getPluginName()
  {
    return "Hero Lab Native";
  }

  @Override
  public List<CreatureTemplate> importCreatures(CreatureImportServices svc, ImportMessageLog log)
      throws ImportCreatureException
  {
    java.util.List<File> files = svc.chooseFiles( this );
    
    lg.debug("User chose files: " + ObjectCommon.formatList(files));
    
    List<CreatureTemplate> allCreatures = new LinkedList<>();
    
    for(File file : files)
    {
      List<CreatureTemplate> creatures = processPortfolio(file, svc);
      if(!creatures.isEmpty())
      {
        lg.info("Imported " + creatures.size() + " Creatures.");
        allCreatures.addAll(creatures);
      }
    }
    
    return allCreatures;
  }

  private List<CreatureTemplate> processPortfolio(File file, CreatureImportServices svc)
  {
    lg.info("Processing Portfolio: " + file);
    
    ProcessPortfolioWRP wrp = new ProcessPortfolioWRP(file);
    wrp.showDialog(svc.accessAnchor());
    
    
    Zipper zipper;
    try
    {
      zipper = new Zipper(file);
    }
    catch(Exception e)
    {
      lg.error("Failed to access zip archive", e);
      return Collections.emptyList();
    }
    
    List<CreatureTemplate> creatures = new LinkedList<>();
    
    for(StatBlockHandle handle : wrp.peekSelectedCreatures())
    {
      try
      {
        creatures.addAll( new HeroLabImportLogic(handle, zipper, svc).importCreature() );
      }
      catch(Exception e)
      {
        lg.error("Failed to import Creatures from: " + handle);
      }
    }
    
    return creatures;
  }
  
  @Override
  public FileFilterForExtension getPluginFileFilter()
  {
    return new FileFilterForExtension( "por", "Hero Lab Portfolio" );
  }
}
