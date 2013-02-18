var fs = require('fs');
var path = require('path');
var spawn = require('child_process').spawn;

var tests = [];
(function() {
  var files = fs.readdirSync(__dirname);
  for (var i = 0; i < files.length; i++) {
    var file = files[i];
    if (file.match(/\.dorp$/)) {
      tests.push(path.join(__dirname, file));
    }
  }
})();

var failzors = [];
var done = 0;
(function() {
  for (var i = 0; i < tests.length; i++)
    run_test(tests[i], function(actual_output, expected_output) {
      if (actual_output === expected_output) {
        process.stdout.write(".");
      } else {
        process.stdout.write("!");
        failzors.push(actual_output);
      }
      done++;
      if (done >= tests.length) {
        console.log("");
        for (var i = 0; i < failzors.length; i++)
          console.log(failzors[i]);
      }
    });
})();

function run_test(test, cb) {
  var contents = fs.readFileSync(test, 'utf8');
  var lines = contents.split("\n");
  var expected_output = "";
  for (var i = 0; i < lines.length; i++) {
    var match = lines[i].match(/# (.*)/);
    if (match) {
      expected_output += match[1] + "\n";
    }
  }
  var child = spawn('node', [path.join(__dirname, '..', 'dorp.js'), test], {stdio: 'pipe'});
  var actual_output = "";
  child.stdout.setEncoding('utf8');
  child.stdout.on('data', function(data) {
    actual_output += data;
  });
  child.stderr.setEncoding('utf8');
  child.stderr.on('data', function(data) {
    actual_output += data;
  });
  child.on('exit', function() {
    cb(actual_output, expected_output);
  });
}

