module.exports = function (config) {
  var outputDir = 'target/test/karma'
  config.set({
    frameworks: ['cljs-test'],
    browsers: ['ChromeHeadless'],
    basePath: outputDir,
    files: ['test.js'],
    plugins: [
        'karma-cljs-test',
        'karma-phantomjs-launcher',
        'karma-chrome-launcher',
        'karma-junit-reporter'
    ],
    colors: true,
    logLevel: config.LOG_INFO,
    client: {
      args: ['shadow.test.karma.init'],
      singleRun: true
    },
    junitReporter: {
      outputDir: outputDir + '/junit',
      outputFile: undefined,
      suite: ''
    }
  })
}
