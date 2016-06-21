'use strict';

var gulp = require('gulp');
var watch = require('gulp-watch');
var sequence = require('run-sequence');
var util = require('gulp-util')
var minifyCss = require('gulp-clean-css');
var minifyJs = require('gulp-uglify');
var jshint = require('gulp-jshint');
var concat = require('gulp-concat');
var concatBower = require('gulp-concat-vendor');
var mainBowerFiles = require('main-bower-files');
var sass = require('gulp-sass');
var sourceMaps = require('gulp-sourcemaps');
var del = require('del');
var jasmineBrowser = require('gulp-jasmine-browser');

var config = {
    target: "../../../target/classes/web",
    testTarget: "../../../target/test-classes/web",
    production: !!util.env.production
};

var TARGET = config.target;

var fileForTest = [
    config.testTarget + '/jquery/jquery.min.js',
    config.testTarget + '/jquery/plugins/jquery.form.js',
    config.testTarget + '/js/frameworks/es5-shim-dejavu.js',
    config.testTarget + '/js/view-dropdown.js',
    config.testTarget + '/js/**/*.js',
    'node_modules/jasmine-ajax/lib/mock-ajax.js',
    'test/fixtures/**/*.js',
    'test/**/*_test.js'
];

gulp.task('lib-compile', function () {
    var libJs = gulp.src(mainBowerFiles('**/*.js'),{ base: 'bower_components' })
        .pipe(concatBower('lib.js'))
        .pipe((config.production) ? minifyJs() : util.noop())
        .pipe(gulp.dest(TARGET + '/js'));
});

gulp.task('theme-compile-sass', function () {
    return gulp.src('themes/default/scss/*.scss')
        .pipe((!config.production) ? sourceMaps.init() : util.noop())
        .pipe(sass().on('error', sass.logError))
        .pipe((config.production) ? minifyCss() : util.noop())
        .pipe((!config.production) ? sourceMaps.write() : util.noop())
        .pipe(gulp.dest(TARGET + '/themes/default'));
});

gulp.task('theme-copy-css', function () {
    return gulp.src(['themes/**/*.css'])
        .pipe((config.production) ? minifyCss({processImport: false}) : util.noop())
        .pipe(gulp.dest(TARGET + '/themes'));
});

gulp.task('theme-copy-resources', function () {
    return gulp.src(['themes/**', '!themes/**/scss/*.scss', '!themes/**/*.css'])
        .pipe(gulp.dest(TARGET + '/themes'));
});

gulp.task('jquery-copy', function () {
    return gulp.src('jquery/**')
        .pipe(gulp.dest(TARGET + "/jquery"));
});

gulp.task('flash-copy', function () {
    return gulp.src('flash/**')
        .pipe(gulp.dest(TARGET + "/flash"));
});

gulp.task('js-copy', function () {
    return gulp.src('js/**/*.js')
        .pipe((config.production) ? minifyJs({keep_fnames: true}) : util.noop())
        .pipe(gulp.dest(TARGET + "/js"));
});

gulp.task('js-copy-resources', function () {
    return gulp.src(['js/**', '!js/**/*.js'])
        .pipe(gulp.dest(TARGET + "/js"));
});

gulp.task('ckeditor-copy', function () {
    return gulp.src('CKEditor/ckeditor-build/**')
        .pipe(gulp.dest(TARGET + '/ckeditor-build'));
});

gulp.task('watch', function () {
    gulp.watch('themes/default/scss/*.scss', ['theme-compile-sass']);
    gulp.watch('themes/**/*.css', ['theme-copy-css'])
    gulp.watch('js/**/*.js', ['js-copy']);
});

gulp.task('clean', function () {
    return del(TARGET, {force: true});
});

gulp.task('test-dependencies', function (callback) {
    TARGET = config.testTarget;
    return sequence(
        'clean',
         [
            'js-copy',
            'js-copy-resources',
            'jquery-copy',
            'ckeditor-copy'
         ],
         callback
    );
});

gulp.task('test', ['test-dependencies'], function () {
    return gulp.src(fileForTest)
        .pipe(jasmineBrowser.specRunner({console: true}))
        .pipe(jasmineBrowser.headless());
});

gulp.task('test-browser', ['test-dependencies'], function () {
    gulp.watch('themes/**/*.css', ['theme-copy-css'])
    gulp.watch('js/**/*.js', ['js-copy']);
    return gulp.src(fileForTest)
        .pipe(watch(fileForTest))
        .pipe(jasmineBrowser.specRunner())
        .pipe(jasmineBrowser.server());
});

gulp.task('default', function () {
    return gulp.start(
        'lib-compile',
        'theme-compile-sass',
        'theme-copy-css',
        'theme-copy-resources',
        'js-copy',
        'js-copy-resources',
        'jquery-copy',
        'ckeditor-copy',
        'flash-copy'
    );
});

