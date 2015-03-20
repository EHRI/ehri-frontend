#!/usr/bin/perl

use strict;
use warnings;
use Digest::SHA qw(sha1_hex);


if (scalar(@ARGV) < 1) {
    die "Original path given!\n";
}

my $out = shift(@ARGV);

while (<>) {
    chomp;
    my ($from_id, $to_id) = split /\t/, $_;
    my $from = $out . $from_id;
    my $to = $out . $to_id;
    my $hash = sha1_hex($from);
    print "$hash\t$from\t$to\t\\N\n";
}
