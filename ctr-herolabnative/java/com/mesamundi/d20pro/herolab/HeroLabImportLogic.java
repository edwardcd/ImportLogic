package com.mesamundi.d20pro.herolab;

import static com.d20pro.plugin.api.XMLToDocumentHelper.loadDocument;
import static com.d20pro.plugin.api.XMLToDocumentHelper.peekValueForNamedItem;

import java.io.File;
import java.util.*;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;

import com.d20pro.plugin.api.CreatureImportServices;
import com.d20pro.plugin.api.CreatureImportServices.MapLogic;

import com.sengent.common.control.exception.UserVisibleException;

import com.mesamundi.common.FileCommon;
import com.mesamundi.common.FileCommon.ZipUtil;
import com.mesamundi.common.ObjectCommon;
import com.mesamundi.common.util.Zipper;

import com.mindgene.d20.common.creature.CreatureTemplate;
import com.mindgene.d20.common.util.ImageProvider;

/**
 * 
 * @author thraxxis
 *
 */
final class HeroLabImportLogic
{
  private static final Logger lg = Logger.getLogger(HeroLabImportLogic.class);
  
  private final StatBlockHandle _handle;
  private final Zipper _zipper;
  private final CreatureImportServices _svc;

  private Document _doc;
  
  private interface Path
  {
    String CHARACTER = "/document/public/character";
  }
  
  HeroLabImportLogic(StatBlockHandle handle, Zipper zipper, CreatureImportServices svc)
  {
    _handle = handle;
    _zipper = zipper;
    _svc = svc;
  }
  
  List<CreatureTemplate> importCreature() throws Exception
  {
    lg.debug("Importing: " + _handle);

    byte[] data = _handle.getData();
    if(lg.isTraceEnabled())
      lg.trace("Contents of " + _handle + ": " + new String(data));
    _doc = loadDocument(data);
    
    CreatureTemplate ctr = new CreatureTemplate();
    ctr.setModuleName("Hero Lab");
    
    importBasics(ctr);
    
    // Build the notes tab by combining notes, abilities, and error log
    ctr.buildFullNotes();
    
    Optional<Short> optImage = importImage();
    optImage.ifPresent(id -> {
      ctr.setImageID(id.shortValue());
    });
    
    List<CreatureTemplate> creatures = new LinkedList<>();
    creatures.add(ctr);
    creatures.addAll(importMinions());
    return creatures;
  }
  
  
  private void importBasics(CreatureTemplate ctr) throws UserVisibleException
  {
    ctr.setName(peekValueForNamedItem(_doc, Path.CHARACTER, "name"));
    ctr.setAlignment(peekValueForNamedItem(_doc, Path.CHARACTER + "/alignment", "name"));
    ctr.setExperiencePoints(peekValueForNamedItem(_doc, Path.CHARACTER + "/xp", "total"));
    
    MapLogic[] logics = {
      new MapLogic( "currenthp", val -> { ctr.setHP( Short.parseShort( val ) ); }),
      new MapLogic( "hitpoints", val -> { ctr.setHPMax( Short.parseShort( val ) ); }),
      new MapLogic( "hitdice", val -> { ctr.setHitDice( val ); })
    };
    MapLogic.apply( _doc, Path.CHARACTER + "/health", ctr, logics );
  }
  
  private Optional<Short> importImage()
  {
    Optional<String> optImage = _handle.getImageFilename();
    
    if(optImage.isPresent())
    {
      String imageEntry = optImage.get();
      try
      {
        File tempDest = File.createTempFile(HeroLabImportLogic.class.getSimpleName() + "Image", '.' + FileCommon.getExtension(imageEntry));
        ZipUtil.extractFileFromArchiveToFile(_zipper.peekSourceFile(), imageEntry, tempDest);
        lg.info("Extracted image to temp file: " + tempDest);
        return _svc.accessImageService().assimilateImage(_handle.getDisplayName(), tempDest, ImageProvider.Categories.CREATURE, "Hero Lab", true );
      }
      catch(UserVisibleException uve)
      {
        lg.error( "Image not available: " + ObjectCommon.buildCollapsedExceptionMessage( uve ) );
      }
      catch(Exception e)
      {
        lg.error("Failed to extract image: " + imageEntry, e);
      }
    }
    
    return Optional.empty();
  }
  
  private List<CreatureTemplate> importMinions()
  {
    lg.info("Minions currently not supported");
    return Collections.emptyList();
  }
}
