#!/usr/bin/perl

#
# Find properties present in one file but missing in another.
# e.g. untranslated strings.
#

use strict;
use warnings;
use Config::Properties;

if (scalar(@ARGV) != 2) {
    die "usage: findprops.pl <allprops> <partial>\n";
}

compare_props(parse_props(shift(@ARGV)), parse_props(shift(@ARGV)));

exit(0);

sub compare_props {
    my ($all, $partial) = @_;

    my $data = Config::Properties->new();

    for my $key ($all->propertyNames) {
        if (not defined $partial->getProperty($key)) {
            my $val = $all->getProperty($key);
            if ($val ne "") {
                $data->setProperty($key, $val);
            }
        }
    }

    open(my $OUT_H, '>&', \*STDOUT) or die;
    $data->store($OUT_H);
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

