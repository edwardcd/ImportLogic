package com.mesamundi.d20pro.herolab;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.File;
import java.util.*;
import java.util.zip.ZipException;

import javax.swing.*;

import com.d20pro.plugin.api.XMLToDocumentHelper;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;

import com.mesamundi.common.FileCommon.ZipUtil;
import com.mesamundi.common.ObjectCommon;
import com.mesamundi.common.util.Zipper;

import com.mindgene.d20.LAF;
import com.mindgene.d20.common.D20LF;
import com.mindgene.d20.common.lf.D20OKCancelReadyPanel;
import com.mindgene.d20.laf.BlockerControl;
import com.mindgene.d20.laf.BlockerView;

/**
 * Dialog that displays the contents of a Hero Lab Portfolio.
 * 
 * @author thraxxis
 *
 */
final class ProcessPortfolioWRP extends D20OKCancelReadyPanel
{
  private static final Logger lg = Logger.getLogger(ProcessPortfolioWRP.class);
  
  private final BlockerView _blocker;
  private final File _file;
  
  private List<StatBlockHandle> _handles;
  private List<JCheckBox> _handleChecks;

  ProcessPortfolioWRP(File file)
  {
    _file = file;
    _blocker = LAF.Area.blocker(LAF.Area.clear());
    
    setLayout(new BorderLayout());
    add(_blocker, BorderLayout.CENTER);
    
    setPreferredSize(new Dimension(300, 200));
    setResizable(true);
    
    new LoadPortfolioLogic();
  }

  private interface Entry
  {
    String INDEX = "index.xml";
    String STAT_BLOCK = "statblocks_xml/";
    String IMAGE = "images/";
  }
  
  private class LoadPortfolioLogic extends BlockerControl
  {
    LoadPortfolioLogic()
    {
      super(LoadPortfolioLogic.class, "Loading Portfolio...", _blocker);
      startThread();
    }
    
    @Override
    protected void work()
    {
      try
      {
        Zipper zipper = new Zipper(_file);
        
        lg.trace("Portfolio entries:\n" + ObjectCommon.formatArray(zipper.getCurrentEntries(), "\n"));
        String gameSystem = resolveGameSystem(zipper);
        lg.info("Discovered game system: " + gameSystem);
        
        _handles = resolveStatBlocks(zipper);
        
        SwingUtilities.invokeLater(() -> {
          JPanel area = LAF.Area.clear(0, 2);
          area.setBorder(D20LF.Brdr.padded(4));
          
          JPanel areaHandles = LAF.Area.clear(new GridLayout(0, 1, 0, 2));
          
          _handleChecks = new ArrayList<>(_handles.size());
          for(StatBlockHandle handle : _handles)
          {
            JCheckBox checkHandle = LAF.Check.common(handle.toString());
            checkHandle.setSelected(true);
            _handleChecks.add(checkHandle);
            areaHandles.add(checkHandle);
          }
          
          area.add(D20LF.Pnl.labeled("Game System: ", LAF.Label.left(gameSystem)), BorderLayout.NORTH);
          JScrollPane sPane = LAF.Area.sPaneVertical(LAF.Area.Hugging.top(areaHandles));
          sPane.setBorder(null);
          area.add(sPane, BorderLayout.CENTER);
          
          _blocker.setContent(area);
        });
      }
      catch (Exception e)
      {
        D20LF.Dlg.showError(ProcessPortfolioWRP.this, "Failed to load Portfolio", e);
        disposeWindow();
      }
    }
    
    private String resolveGameSystem(Zipper zipper)
    {
      try
      {
        if(zipper.entryExists(Entry.INDEX))
        {
          byte[] index = ZipUtil.extractFileBytesFromArchive(_file, Entry.INDEX);
          if(lg.isTraceEnabled())
            lg.trace("Contents of " + Entry.INDEX + ": " + new String(index));
          Document doc = XMLToDocumentHelper.loadDocument(index);
          return XMLToDocumentHelper.peekValueForNamedItem(doc, "/document/game", "name");
        }
        else
        {
          lg.warn("Portfolio is missing " + Entry.INDEX);
        }
      }
      catch(Exception e)
      {
        lg.warn("Failed to extract game system", e);
      }
      return "???";
    }
    
    /**
     * List of Pairs where the key is the name of the Creature and the byte[] is its XML stat block.
     * 
     * @param zipper
     * @return
     * @throws ZipException 
     */
    private List<StatBlockHandle> resolveStatBlocks(Zipper zipper) throws Exception
    {
      List<StatBlockHandle> statBlocks = new LinkedList<>();
      
      String[] entries = zipper.getCurrentEntries();
      
      Map<Integer, String> images = resolveImages(entries);
      
      for(String entry : entries)
      {
        if(entry.startsWith(Entry.STAT_BLOCK))
        {
          lg.info("Loading stat block for entry: " + entry);
          byte[] statBlock = ZipUtil.extractFileBytesFromArchive(_file, entry);
          String name = entry.substring(Entry.STAT_BLOCK.length());
          
          int at = name.indexOf('_');
          Integer id = Integer.valueOf(name.substring(0, at));
          Optional<String> optImage = Optional.ofNullable(images.get(id));
          
          statBlocks.add(new StatBlockHandle(name, statBlock, optImage));
        }
      }
      
      return statBlocks;
    }
    
    private Map<Integer, String> resolveImages(String[] entries)
    {
      Map<Integer, String> images = new LinkedHashMap<>();
      
      for(String entry : entries)
      {
        if(entry.startsWith(Entry.IMAGE))
        {
          try
          {
            String name = entry.substring(Entry.IMAGE.length());
            int at = name.indexOf('_');
            Integer id = Integer.valueOf(name.substring(0, at));
            name = name.substring(0, at + 1);
            at = name.indexOf('_');
            if(at == 1)
            {
              lg.info("Image found for ID: " + id);
              images.put(id, entry);
            }
            else
              lg.info("Skipping additional image: " + entry);
          }
          catch(Exception e)
          {
            lg.error("Failed to resolve image for: " + entry, e);
          }
        }
      }
      
      lg.info("Found image entries: " + ObjectCommon.formatCollection(images.values()));
      
      return images;
    }
  }
  
  java.util.List<StatBlockHandle> peekSelectedCreatures()
  {
    int size = _handles.size();
    java.util.List<StatBlockHandle> selected = new ArrayList<>(size);
    for(int i = 0; i < size; i++)
    {
      if(_handleChecks.get(i).isSelected())
        selected.add(_handles.get(i));
    }
    
    return selected;
  }
  
  @Override
  public String getTitle()
  {
    return "Portfolio Summary";
  }
}
