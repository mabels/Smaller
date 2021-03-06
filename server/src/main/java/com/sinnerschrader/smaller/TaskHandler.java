package com.sinnerschrader.smaller;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.Body;
import org.apache.camel.Property;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.isdc.wro.config.Context;
import ro.isdc.wro.config.jmx.WroConfiguration;
import ro.isdc.wro.extensions.processor.css.LessCssProcessor;
import ro.isdc.wro.extensions.processor.css.SassCssProcessor;
import ro.isdc.wro.extensions.processor.css.YUICssCompressorProcessor;
import ro.isdc.wro.extensions.processor.js.CoffeeScriptProcessor;
import ro.isdc.wro.extensions.processor.js.GoogleClosureCompressorProcessor;
import ro.isdc.wro.extensions.processor.js.UglifyJsProcessor;
import ro.isdc.wro.http.support.DelegatingServletOutputStream;
import ro.isdc.wro.manager.factory.WroManagerFactory;
import ro.isdc.wro.manager.factory.standalone.ConfigurableStandaloneContextAwareManagerFactory;
import ro.isdc.wro.manager.factory.standalone.StandaloneContext;
import ro.isdc.wro.model.WroModel;
import ro.isdc.wro.model.factory.WroModelFactory;
import ro.isdc.wro.model.group.Group;
import ro.isdc.wro.model.resource.Resource;
import ro.isdc.wro.model.resource.ResourceType;
import ro.isdc.wro.model.resource.processor.ResourcePreProcessor;
import ro.isdc.wro.model.resource.processor.factory.ConfigurableProcessorsFactory;
import ro.isdc.wro.model.resource.processor.impl.css.CssDataUriPreProcessor;

import com.sinnerschrader.smaller.common.Manifest;
import com.sinnerschrader.smaller.common.Manifest.Task;

/**
 * @author marwol
 */
public class TaskHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(TaskHandler.class);

  private CoffeeScriptProcessor coffeeScriptProcessor = new CoffeeScriptProcessor();

  private GoogleClosureCompressorProcessor googleClosureCompressorProcessor = new GoogleClosureCompressorProcessor();

  private UglifyJsProcessor uglifyJsProcessor = new UglifyJsProcessor();

  private LessCssProcessor lessCssProcessor = new LessCssProcessor();

  private SassCssProcessor sassCssProcessor = new SassCssProcessor();

  private CssDataUriPreProcessor cssDataUriPreProcessor = new CssDataUriPreProcessor();

  private YUICssCompressorProcessor yuiCssCompressorProcessor = new YUICssCompressorProcessor();

  /**
   * @param main
   * @return the route name of the next step
   */
  public String runTask(@Body Manifest main) {
    Task task = main.getNext();
    if (task == null) {
      LOGGER.info("Finished processing");
      return null;
    }
    String processor = StringUtils.capitalize(task.getProcessor().toLowerCase());
    if (processor.contains(",")) {
      processor = "Any";
    }
    String nextRoute = "direct:run" + processor;
    LOGGER.info("Next Route: {}", nextRoute);
    return nextRoute;
  }

  /**
   * @param input
   * @param output
   * @param main
   * @throws IOException
   */
  public void runAny(@Property(Router.PROP_INPUT) final File input, @Property(Router.PROP_OUTPUT) final File output, @Body Manifest main) throws IOException {
    LOGGER.debug("TaskHandler.runAny()");
    final Task task = main.getCurrent();
    runTool("js", task.getProcessor(), input, output, main);
    runTool("css", task.getProcessor(), input, output, main);
  }

  /**
   * @param input
   * @param output
   * @param main
   * @throws IOException
   */
  public void runCoffeeScript(@Property(Router.PROP_INPUT) final File input, @Property(Router.PROP_OUTPUT) final File output, @Body Manifest main)
      throws IOException {
    runJsTool("coffeeScript", input, output, main);
  }

  /**
   * @param input
   * @param output
   * @param main
   * @throws IOException
   */
  public void runClosure(@Property(Router.PROP_INPUT) final File input, @Property(Router.PROP_OUTPUT) final File output, @Body Manifest main)
      throws IOException {
    runJsTool("closure", input, output, main);
  }

  /**
   * @param input
   * @param output
   * @param main
   * @throws IOException
   */
  public void runUglifyJs(@Property(Router.PROP_INPUT) final File input, @Property(Router.PROP_OUTPUT) final File output, @Body Manifest main)
      throws IOException {
    runJsTool("uglifyjs", input, output, main);
  }

  /**
   * @param input
   * @param output
   * @param main
   * @throws IOException
   */
  public void runLessJs(@Property(Router.PROP_INPUT) final File input, @Property(Router.PROP_OUTPUT) final File output, @Body Manifest main) throws IOException {
    runCssTool("lessjs", input, output, main);
  }

  /**
   * @param input
   * @param output
   * @param main
   * @throws IOException
   */
  public void runSass(@Property(Router.PROP_INPUT) final File input, @Property(Router.PROP_OUTPUT) final File output, @Body Manifest main) throws IOException {
    runCssTool("sass", input, output, main);
  }

  /**
   * @param input
   * @param output
   * @param main
   * @throws IOException
   */
  public void runCssEmbed(@Property(Router.PROP_INPUT) final File input, @Property(Router.PROP_OUTPUT) final File output, @Body Manifest main)
      throws IOException {
    runCssTool("cssembed", input, output, main);
  }

  /**
   * @param input
   * @param output
   * @param main
   * @throws IOException
   */
  public void runYuiCompressor(@Property(Router.PROP_INPUT) final File input, @Property(Router.PROP_OUTPUT) final File output, @Body Manifest main)
      throws IOException {
    runCssTool("yuiCompressor", input, output, main);
  }

  private void runJsTool(final String tool, final File input, File output, Manifest main) throws IOException {
    runTool("js", tool, input, output, main);
  }

  private void runCssTool(final String tool, final File input, File output, Manifest main) throws IOException {
    runTool("css", tool, input, output, main);
  }

  private void runTool(String type, final String tool, final File input, File output, Manifest main) throws IOException {
    LOGGER.debug("TaskHandler.runTool('{}', '{}', '{}', {})", new Object[] { type, tool, input, main });
    final Task task = main.getCurrent();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    runInContext("all", type, baos, new Callback() {
      public void runWithContext(HttpServletRequest request, HttpServletResponse response) throws IOException {
        WroManagerFactory managerFactory = getManagerFactory(getWroModelFactory(task, input), tool, null);
        managerFactory.create().process();
        managerFactory.destroy();
      }
    });
    String target = getOutputFile(task.getOut(), ResourceType.valueOf(type.toUpperCase()));
    FileUtils.writeByteArrayToFile(new File(output, target), baos.toByteArray());
  }

  private WroManagerFactory getManagerFactory(WroModelFactory modelFactory, final String preProcessors, final String postProcessors) {
    ConfigurableStandaloneContextAwareManagerFactory cscamf = new ConfigurableStandaloneContextAwareManagerFactory() {
      @Override
      protected Properties createProperties() {
        Properties properties = new Properties();
        if (StringUtils.isNotBlank(preProcessors)) {
          properties.setProperty(ConfigurableProcessorsFactory.PARAM_PRE_PROCESSORS, preProcessors);
        }
        if (StringUtils.isNotBlank(postProcessors)) {
          properties.setProperty(ConfigurableProcessorsFactory.PARAM_POST_PROCESSORS, postProcessors);
        }
        return properties;
      }

      @Override
      protected Map<String, ResourcePreProcessor> createPreProcessorsMap() {
        Map<String, ResourcePreProcessor> map = super.createPreProcessorsMap();
        map.put("coffeeScript", coffeeScriptProcessor);
        map.put("uglifyjs", uglifyJsProcessor);
        map.put("lessjs", lessCssProcessor);
        map.put("sass", sassCssProcessor);
        map.put("cssembed", cssDataUriPreProcessor);
        map.put("closure", googleClosureCompressorProcessor);
        map.put("yuiCompressor", yuiCssCompressorProcessor);
        return map;
      }
    };
    StandaloneContext standaloneContext = new StandaloneContext();
    standaloneContext.setMinimize(true);
    cscamf.initialize(standaloneContext);
    cscamf.setModelFactory(modelFactory);
    return cscamf;
  }

  /**
   * @param base
   * @return a wro model with one group 'all' and all input parameters
   * @throws IOException
   */
  private WroModelFactory getWroModelFactory(final Task task, final File base) throws IOException {
    final List<String> input = new ArrayList<String>();
    for (String s : task.getIn()) {
      String ext = FilenameUtils.getExtension(s);
      if ("json".equals(ext)) {
        ObjectMapper om = new ObjectMapper();
        input.addAll(Arrays.asList(om.readValue(new File(base, s), String[].class)));
      } else {
        input.add(s);
      }
    }
    return new WroModelFactory() {

      public WroModel create() {
        Group group = new Group("all");
        for (String i : input) {
          group.addResource(Resource.create(new File(base, i).toURI().toString(), getResourceType(i)));
        }
        return new WroModel().addGroup(group);
      }

      public void destroy() {
      }
    };
  }

  private ResourceType getResourceType(String in) {
    String ext = FilenameUtils.getExtension(in);
    if ("css".equals(ext) || "less".equals(ext) || "sass".equals(ext)) {
      return ResourceType.CSS;
    }
    return ResourceType.JS;
  }

  private String getOutputFile(String[] files, ResourceType type) {
    for (String file : files) {
      if (getResourceType(file) == type) {
        return file;
      }
    }
    throw new RuntimeException("No output file specified for type " + type);
  }

  private void runInContext(String group, String type, OutputStream out, Callback callback) throws IOException {
    Context.set(Context.standaloneContext());
    try {
      HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
      Mockito.when(request.getRequestURI()).thenReturn(group + '.' + type);

      HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
      Mockito.when(response.getOutputStream()).thenReturn(new DelegatingServletOutputStream(out));

      final WroConfiguration config = new WroConfiguration();
      config.setParallelPreprocessing(false);

      Context.set(Context.webContext(request, response, Mockito.mock(FilterConfig.class)), config);
      try {
        callback.runWithContext(request, response);
      } finally {
        Context.unset();
      }
    } finally {
      Context.unset();
    }
  }

  private interface Callback {

    void runWithContext(HttpServletRequest request, HttpServletResponse response) throws IOException;
  }

}
