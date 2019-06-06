#!/usr/bin/perl

#
# Find properties present in one file but missing in another.
# e.g. untranslated strings.
#

use v5.10;
use utf8;

use strict;
use warnings qw(FATAL utf8);
use Config::Properties;
use Text::CSV qw(csv);
use Getopt::Long qw(GetOptions);
use open qw(:utf8 :std);

my $is_csv = 0;

GetOptions("c|csv" => \$is_csv) or die ("Error in command line arguments");

if (scalar(@ARGV) != 2) {
    die "usage: findprops.pl <allprops> <partial>\n";
}

my @data = compare_props(parse_props(shift(@ARGV)), parse_props(shift(@ARGV)));

if ($is_csv) {
    my $csv = Text::CSV->new ({ binary => 0, auto_diag => 1 });
    $csv->say(*STDOUT, $_) for @data;
} else {
    my $conf = Config::Properties->new();
    $conf->setProperty(@{$_}) for @data;
    $conf->store(\*STDOUT);
}

exit(0);


sub compare_props {
    my ($all, $partial) = @_;

    my @data = ();

    for my $key ($all->propertyNames) {
        if (not defined $partial->getProperty($key)) {
            my $val = $all->getProperty($key);
            $val =~ s/\s*\\\s*\R\s+/ /mg;
            if ($val) {
                push @data, [$key, $val];  
            }
        }
    }

    return @data;
}

sub parse_props {
    my $filename = shift;

    my $data = {};

    open(my $F_H, "<:encoding(utf8)", $filename) or die "Unable to open file $filename!: $!\n";
    binmode($F_H, ":utf8");
    my $properties = Config::Properties->new();
    $properties->load($F_H);
    close($F_H);

    $properties;
}

