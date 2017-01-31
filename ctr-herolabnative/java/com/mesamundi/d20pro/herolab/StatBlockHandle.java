package com.mesamundi.d20pro.herolab;

import java.util.Optional;

/**
 * 
 * @author thraxxis
 *
 */
final class StatBlockHandle
{
  private final String _filename;
  private final String _displayName;
  private final byte[] _data;
  private final Optional<String> _imageFilename;
  
  StatBlockHandle(String filename, byte[] data, Optional<String> optImageFilename)
  {
    _filename = filename;
    _displayName = resolveDisplayName(filename);
    _data = data;
    _imageFilename = optImageFilename;
  }
  
  static String resolveDisplayName(String filename)
  {
    String displayName = filename.substring(filename.indexOf('_') + 1);
    displayName = displayName.substring(0, displayName.length() - ".xml".length());
    return displayName;
  }
  
  public String getFilename()
  {
    return _filename;
  }

  public String getDisplayName()
  {
    return _displayName;
  }

  public byte[] getData()
  {
    return _data;
  }

  public Optional<String> getImageFilename()
  {
    return _imageFilename;
  }

  @Override
  public String toString()
  {
    return _displayName;
  }
}
