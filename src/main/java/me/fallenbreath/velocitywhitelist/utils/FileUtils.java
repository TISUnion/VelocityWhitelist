package me.fallenbreath.velocitywhitelist.utils;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class FileUtils
{
	public static void safeWrite(Path path, Path tempPath, String content) throws IOException
	{
		Files.writeString(tempPath, content, StandardCharsets.UTF_8);
		Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
	}

	public static void dumpYaml(Path path, Path tempPath, Object data) throws IOException
	{
		DumperOptions dumperOptions = new DumperOptions();
		dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

		String yamlContent = new Yaml(dumperOptions).dump(data);
		safeWrite(path, tempPath, yamlContent);
	}
}
